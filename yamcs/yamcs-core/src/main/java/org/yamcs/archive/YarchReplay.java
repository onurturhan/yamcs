package org.yamcs.archive;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.SpeedLimitStream;
import org.yamcs.yarch.SpeedSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Performs a replay from Yarch So far supported are: TM packets, PP groups, Events, Parameters and Command History.
 * 
 * It relies on handlers for each data type. Each handler creates a stream, the streams are merged and the output is
 * sent to the listener This class can also handle pause/resume: simply stop sending data seek: closes the streams and
 * creates new ones with a different starting time.
 * 
 * @author nm
 *
 */
public class YarchReplay implements StreamSubscriber {
    ReplayServer replayServer;
    volatile String streamName;
    volatile boolean quitting = false;
    private volatile ReplayState state = ReplayState.INITIALIZATION;
    static Logger log = LoggerFactory.getLogger(YarchReplay.class.getName());
    private volatile String errorString = "";
    int numPacketsSent;
    final String instance;
    static AtomicInteger counter = new AtomicInteger();
    XtceDb xtceDb;

    volatile ReplayOptions currentRequest;

    Map<ProtoDataType, ReplayHandler> handlers;

    private Semaphore pausedSemaphore = new Semaphore(0);
    boolean dropTuple = false; // set to true when jumping to a different time
    volatile boolean ignoreClose;
    ReplayListener listener;

    public YarchReplay(ReplayServer replayServer, ReplayOptions rr, ReplayListener listener, XtceDb xtceDb)
            throws YamcsException {
        this.listener = listener;
        this.replayServer = replayServer;
        this.xtceDb = xtceDb;
        this.instance = replayServer.getYamcsInstance();
        setRequest(rr);
    }

    private void setRequest(ReplayOptions req) throws YamcsException {
        if (state != ReplayState.INITIALIZATION && state != ReplayState.STOPPED) {
            throw new YamcsException("changing the request only supported in the INITIALIZATION and STOPPED states");
        }

        if (log.isDebugEnabled()) {
            log.debug("Replay request for time: [{}, {}]",
                    (req.hasStart() ? TimeEncoding.toString(req.getStart()) : "-"),
                    (req.hasStop() ? TimeEncoding.toString(req.getStop()) : "-"));
        }

        if (req.hasStart() && req.hasStop() && req.getStart() > req.getStop()) {
            log.warn("throwing new packetexception: stop time has to be greater than start time");
            throw new YamcsException("stop has to be greater than start");
        }

        currentRequest = req;
        handlers = new HashMap<>();

        if (currentRequest.hasParameterRequest()) {
            throw new YamcsException(
                    "The replay cannot handle directly parameters. Please create a replay processor for that");
        }

        if (currentRequest.hasEventRequest()) {
            handlers.put(ProtoDataType.EVENT, new EventReplayHandler());
        }
        if (currentRequest.hasPacketRequest()) {
            handlers.put(ProtoDataType.TM_PACKET, new XtceTmReplayHandler(xtceDb));
        }
        if (currentRequest.hasPpRequest()) {
            handlers.put(ProtoDataType.PP, new ParameterReplayHandler(xtceDb));
        }
        if (currentRequest.hasCommandHistoryRequest()) {
            handlers.put(ProtoDataType.CMD_HISTORY, new CommandHistoryReplayHandler(instance));
        }

        for (ReplayHandler rh : handlers.values()) {
            rh.setRequest(req);
        }
    }

    public ReplayState getState() {
        return state;
    }

    public synchronized void start() {
        switch (state) {
        case RUNNING:
            log.warn("start called when already running, call ignored");
            return;
        case INITIALIZATION:
        case STOPPED:
            try {
                initReplay();
                state = ReplayState.RUNNING;
            } catch (Exception e) {
                log.error("Got exception when creating the stream: ", e);
                errorString = e.toString();
                state = ReplayState.ERROR;
            }
            break;
        case PAUSED:
            state = ReplayState.RUNNING;
            pausedSemaphore.release();
            break;
        case ERROR:
        case CLOSED:
            // do nothing?
        }
    }

    private void initReplay() throws StreamSqlException, ParseException {
        streamName = "replay_stream" + counter.incrementAndGet();

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE STREAM " + streamName + " AS ");

        if (handlers.size() > 1) {
            sb.append("MERGE ");
        }

        boolean first = true;
        for (ReplayHandler rh : handlers.values()) {
            String selectCmd = rh.getSelectCmd();
            if (selectCmd != null) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                if (handlers.size() > 1) {
                    sb.append("(");
                }
                sb.append(selectCmd);
                if (handlers.size() > 1) {
                    sb.append(")");
                }
            }
        }

        if (first) {
            if (currentRequest.getEndAction() == EndAction.QUIT) {
                signalStateChange();
            }
            return;
        }

        if (handlers.size() > 1) {
            sb.append(" USING gentime");
        }

        if (handlers.size() > 1 && currentRequest.isReverse()) {
            sb.append(" ORDER DESC");
        }

        ReplaySpeed rs = currentRequest.getSpeed();

        switch (rs.getType()) {
        case AFAP:
        case STEP_BY_STEP: // Step advancing is controlled from within this class
            sb.append(" SPEED AFAP");
            break;
        case FIXED_DELAY:
            sb.append(" SPEED FIXED_DELAY " + (long) rs.getParam());
            break;
        case REALTIME:
            sb.append(" SPEED ORIGINAL gentime," + (long) rs.getParam());
            break;
        }

        String query = sb.toString();
        log.debug("running query {}", query);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        ydb.execute(query);
        Stream s = ydb.getStream(streamName);
        s.addSubscriber(this);
        numPacketsSent = 0;
        s.start();
    }

    public void seek(long newReplayTime) throws YamcsException {
        if (state != ReplayState.INITIALIZATION) {
            boolean wasPaused = (state == ReplayState.PAUSED);
            state = ReplayState.INITIALIZATION;
            String query = "CLOSE STREAM " + streamName;
            ignoreClose = true;
            try {
                YarchDatabaseInstance db = YarchDatabase.getInstance(instance);
                if (db.getStream(streamName) != null) {
                    log.debug("running query: {}", query);
                    db.executeDiscardingResult(query);
                } else {
                    log.debug("Stream already closed");
                }

                // Do this _after_ the stream was closed, to prevent more tuples
                // from the (paused) stream to be pushed.
                if (wasPaused) {
                    dropTuple = true;
                    pausedSemaphore.release();
                }
            } catch (Exception e) {
                log.error("Got exception when closing the stream: ", e);
                errorString = e.toString();
                state = ReplayState.ERROR;
                signalStateChange();
            }
        }
        currentRequest.setStart(newReplayTime);
        for (ReplayHandler rh : handlers.values()) {
            rh.setRequest(currentRequest);
        }
        start();
    }

    public void changeSpeed(ReplaySpeed newSpeed) {
        log.debug("Changing speed to {}", newSpeed);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream s = ydb.getStream(streamName);
        if (!(s instanceof SpeedLimitStream)) {
            throw new IllegalStateException("Cannot change speed on a " + s.getClass() + " stream");
        } else {
            ((SpeedLimitStream) s).setSpeedSpec(toSpeedSpec(newSpeed));
        }
        currentRequest.setSpeed(newSpeed);
    }

    private SpeedSpec toSpeedSpec(ReplaySpeed speed) {
        SpeedSpec ss;
        switch (speed.getType()) {
        case AFAP:
        case STEP_BY_STEP: // Step advancing is controlled from within this class
            ss = new SpeedSpec(SpeedSpec.Type.AFAP);
            break;
        case FIXED_DELAY:
            ss = new SpeedSpec(SpeedSpec.Type.FIXED_DELAY, (int) speed.getParam());
            break;
        case REALTIME:
            ss = new SpeedSpec(SpeedSpec.Type.ORIGINAL, "gentime", speed.getParam());
            break;
        default:
            throw new IllegalArgumentException("Unknown speed type " + speed.getType());
        }
        return ss;
    }

    public void pause() {
        state = ReplayState.PAUSED;
    }

    public synchronized void quit() {
        if (quitting) {
            return;
        }
        quitting = true;
        log.debug("Replay quitting");

        try {
            YarchDatabaseInstance db = YarchDatabase.getInstance(instance);
            if (db.getStream(streamName) != null) {
                db.execute("close stream " + streamName);
            }
        } catch (Exception e) {
            log.error("Exception whilst quitting", e);
        }
        replayServer.replayFinished();
    }

    @Override
    public void onTuple(Stream s, Tuple t) {
        if (quitting) {
            return;
        }
        try {
            while (state == ReplayState.PAUSED) {
                pausedSemaphore.acquire();
            }
            if (dropTuple) {
                dropTuple = false;
                return;
            }

            ProtoDataType type = ProtoDataType.forNumber((Integer) t.getColumn(0));
            Object data = handlers.get(type).transform(t);
            if (data != null) {
                listener.newData(type, data);
            }

            if (currentRequest.getSpeed().getType() == ReplaySpeedType.STEP_BY_STEP) {
                // Force user to trigger next step.
                state = ReplayState.PAUSED;
                signalStateChange();
            }
        } catch (Exception e) {
            if (!quitting) {
                log.warn("Exception received: ", e);
                quit();
            }
        }
    }

    @Override
    public synchronized void streamClosed(Stream stream) {
        if (ignoreClose) { // this happens when we close the stream to reopen
                           // another one
            ignoreClose = false;
            return;
        }

        if (currentRequest.getEndAction() == EndAction.QUIT) {
            state = ReplayState.CLOSED;
            signalStateChange();
            quit();
        } else if (currentRequest.getEndAction() == EndAction.STOP) {
            state = ReplayState.STOPPED;
            signalStateChange();
        } else if (currentRequest.getEndAction() == EndAction.LOOP) {
            if (numPacketsSent == 0) {
                state = ReplayState.STOPPED; // there is no data in this stream
                signalStateChange();
            } else {
                state = ReplayState.INITIALIZATION;
                start();
            }
        }
    }

    private void signalStateChange() {
        try {
            if (quitting) {
                return;
            }
            ReplayStatus.Builder rsb = ReplayStatus.newBuilder().setState(state);
            if (state == ReplayState.ERROR) {
                rsb.setErrorMessage(errorString);
            }
            ReplayStatus rs = rsb.build();
            listener.stateChanged(rs);

        } catch (Exception e) {
            log.warn("got exception while signaling the state change: ", e);
        }
    }

    public ReplayRequest getCurrentReplayRequest() {
        return currentRequest.toProtobuf();
    }

}
