package org.yamcs.archive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.naming.ConfigurationException;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.NotThreadSafe;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.ThreadSafe;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Completeness index of CCSDS telemetry. The structure of the rocksdb records:
 *
 * <pre>
 * key: tbsIndex[4 bytes], apid[2bytes], start time[8 bytes], start seq count[2 bytes]
 * value: end time[8bytes], end seq count[2 bytes], num packets [4 bytes]
 * </pre>
 *
 * FIXME: because the sequence count wraps around, there is a bug in case packets with the same timestamp and wrapped
 * around sequence counts are received - see testApidIndexSameTimeAndWraparound for failing test. the old TokyoCabinet
 * based indexer didn't use the sequence count as part of the key but allowed multiple records with the same key. To
 * replicate this in RocksDB, one would need to have the RocksDB entries composed of all records with the same startime
 *
 * @author nm
 *
 */
@ThreadSafe
public class CcsdsTmIndex extends AbstractYamcsService implements TmIndexService {
    static final String TM_INDEX_NAME = "CCSDS";

    // if time between two packets with the same apid is more than one hour,
    // make two records even if they packets are in sequence (because maybe there is a wrap around involved)
    static long maxApidInterval = 3600 * 1000l;
    private static AtomicInteger streamCounter = new AtomicInteger();
    protected Tablespace tablespace;
    int tbsIndex;
    List<String> streamNames;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration args) throws InitException {
        super.init(yamcsInstance, serviceName, args);
        if (config.containsKey("streams")) {
            streamNames = config.getList("streams");
        } else {
            streamNames = StreamConfig.getInstance(yamcsInstance)
                    .getEntries(StandardStreamType.TM)
                    .stream()
                    .map(sce -> sce.getName())
                    .collect(Collectors.toList());
        }
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        try {
            openDb();
        } catch (RocksDBException e) {
            throw new InitException("Failed to open rocksdb", e);
        }
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        for (String s : streamNames) {
            Stream stream = ydb.getStream(s);
            if (stream == null) {
                notifyFailed(new ConfigurationException("Stream " + s + " does not exist"));
                return;
            }
            stream.addSubscriber(this);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        for (String s : streamNames) {
            Stream stream = ydb.getStream(s);
            if (stream != null) {
                stream.removeSubscriber(this);
            }
        }
        notifyStopped();
    }

    private void openDb() throws RocksDBException {
        List<TablespaceRecord> l = tablespace.filter(Type.TM_INDEX, yamcsInstance,
                tr -> !tr.hasTmIndexName() || TM_INDEX_NAME.equals(tr.getTmIndexName()));
        TablespaceRecord tbr;
        if (l.isEmpty()) {
            tbr = tablespace.createMetadataRecord(yamcsInstance,
                    TablespaceRecord.newBuilder().setType(Type.TM_INDEX).setTmIndexName(TM_INDEX_NAME));
            // add a record at the beginning and at the end to make sure the cursor doesn't run out
            YRDB db = tablespace.getRdb();
            byte[] v = new byte[Record.VAL_SIZE];
            db.put(Record.key(tbr.getTbsIndex(), (short) 0, (long) 0, (short) 0), v);
            db.put(Record.key(tbr.getTbsIndex(), Short.MAX_VALUE, Long.MAX_VALUE, Short.MAX_VALUE), v);

        } else {
            tbr = l.get(0);
        }
        tbsIndex = tbr.getTbsIndex();
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        byte[] packet = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        long time = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        if (packet.length < 7) {
            log.warn("Short packet (size : {}) received by the CcsdsTmIndex Ignored.", packet.length);
            return;
        }
        short apid = CcsdsPacket.getAPID(packet);
        short seq = (short) CcsdsPacket.getSequenceCount(packet);
        try {
            addPacket(apid, time, seq);
        } catch (RocksDBException e) {
            log.error("got exception while saving the packet into index", e);
        }
    }

    synchronized void addPacket(short apid, long instant, short seq) throws RocksDBException {
        YRDB db = tablespace.getRdb();
        RocksIterator it = tablespace.getRdb().newIterator();
        try {
            it.seek(Record.key(tbsIndex, apid, instant, seq));

            // go to the right till we find a record bigger than the packet
            int cright, cleft;
            Record rright, rleft;
            while (true) {
                assert (it.isValid());
                rright = new Record(it.key(), it.value());
                cright = compare(apid, instant, seq, rright);
                if (cright == 0) { // duplicate packet
                    if (log.isTraceEnabled()) {
                        log.trace("ignored duplicate packet: apid={} time={} seq={}", apid,
                                TimeEncoding.toOrdinalDateTime(instant), seq);
                    }
                    return;
                } else if (cright < 0) {
                    break;
                } else {
                    it.next();
                }
            }

            it.prev();
            rleft = new Record(it.key(), it.value());

            cleft = compare(apid, instant, seq, rleft);
            if (cleft == 0) {// duplicate packet
                if (log.isTraceEnabled()) {
                    log.trace("ignored duplicate packet: apid={} time={} seq={}", apid,
                            TimeEncoding.toOrdinalDateTime(instant), seq);
                }
                return;
            }
            // the cursor is located on the left record and we have a few cases to examine
            if ((cleft == 1) && (cright == -1)) { // left and right have to be merged
                rleft.seqLast = rright.seqLast;
                rleft.lastTime = rright.lastTime;
                rleft.numPackets += rright.numPackets + 1;
                db.put(rleft.key(tbsIndex), rleft.val());
                db.delete(rright.key(tbsIndex)); // remove the right record
            } else if (cleft == 1) {// attach to left
                rleft.seqLast = seq;
                rleft.lastTime = instant;
                rleft.numPackets++;
                db.put(rleft.key(tbsIndex), rleft.val());
            } else if (cright == -1) {// attach to right
                db.delete(rright.key(tbsIndex));
                rright.seqFirst = seq;
                rright.firstTime = instant;
                rright.numPackets++;
                db.put(rright.key(tbsIndex), rright.val());
            } else { // create a new record
                Record r = new Record(apid, instant, seq, 1);
                db.put(r.key(tbsIndex), r.val());
            }
        } finally {
            it.close();
        }
    }

    /**
     * compare the packet with the record. returns:
     * <ul>
     * <li>&lt;-1 packet fits at the right and is not attached
     * <li>-1 packet fits at the right and is attached
     * <li>0 packet fits inside
     * <li>1 packet fits at the left and is attached
     * <li>&gt;1 packet fits at the right and is not attached
     * </ul>
     */
    private static int compare(short apid, long time, short seq, Record ar) {
        short arapid = ar.apid();
        if (apid != arapid) {
            return 0x3FFF * Integer.signum(apid - arapid);
        }
        int c = compare(time, seq, ar.firstTime(), ar.firstSeq());
        if (c <= 0) {
            return c;
        }
        c = compare(time, seq, ar.lastTime(), ar.lastSeq());
        if (c >= 0) {
            return c;
        }
        return 0;
    }

    /**
     * Compares two packets (assuming apid is the same) and returns the same thing like the function above
     *
     * @param time1
     * @param seq1
     * @param time2
     * @param seq2
     * @return
     */
    static int compare(long time1, short seq1, long time2, short seq2) {
        if (time1 < time2) {
            if (((time2 - time1) <= maxApidInterval) && (((seq2 - seq1) & 0x3FFF) == 1)) {
                return -1;
            } else {
                return -0x3FFF;
            }
        } else if (time1 == time2) {
            int d = (seq1 - seq2) & 0x3FFF;
            if (d < 0x2000) {
                return d;
            }
            return d - 0x4000;
        } else {
            if (((time1 - time2) <= maxApidInterval) && (((seq1 - seq2) & 0x3FFF) == 1)) {
                return 1;
            } else {
                return 0x3FFF;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.yamcs.yarch.usoc.TmIndex#deleteRecords(long, long)
     */
    @Override
    public synchronized void deleteRecords(long start, long stop) {
        try {
            deleteRecords(new TimeInterval(start, stop));
        } catch (RocksDBException e) {
            log.error("Error when deleting records from the ccsdstmindex", e);
        }
    }

    public void printApidDb() throws RocksDBException {
        printApidDb((short) -1, -1L, -1L);
    }

    private void printApidDb(short apid, long start, long stop, RocksIterator cur) {
        String format = "%-10d  %-30s - %-30s  %12d - %12d";
        Record ar;
        if (start != -1) {
            cur.seek(Record.key(tbsIndex, apid, start, (short) 0));
            cur.prev();
            ar = new Record(cur.key(), cur.value());
            if ((ar.apid() != apid) || (ar.lastTime() < start)) {
                cur.next();
            }
        } else {
            cur.seek(Record.key(tbsIndex, apid, 0L, (short) 0));
        }

        while (true) {
            ar = new Record(cur.key(), cur.value());
            if (ar.apid() != apid) {
                break;
            }
            if ((stop != -1) && (ar.firstTime() > stop)) {
                break;
            }
            System.out.println(String.format(format, ar.apid(), TimeEncoding.toOrdinalDateTime(ar.firstTime()),
                    TimeEncoding.toOrdinalDateTime(ar.lastTime()), ar.firstSeq(), ar.lastSeq()));
            cur.next();
        }
    }

    public void printApidDb(short apid, long start, long stop) throws RocksDBException {
        String formatt = "%-10s  %-30s - %-30s  %12s - %12s";
        System.out.println(String.format(formatt, "apid", "start", "stop", "startseq", "stopseq"));
        try (RocksIterator cur = tablespace.getRdb().newIterator()) {
            Record ar;
            if (apid != -1) {
                printApidDb(apid, start, stop, cur);
            } else {
                apid = 0;
                while (true) {// loop through apids
                    cur.seek(Record.key(tbsIndex, apid, Long.MAX_VALUE, Short.MAX_VALUE));
                    ar = new Record(cur.key(), cur.value());
                    apid = ar.apid();
                    if (apid == Short.MAX_VALUE) {
                        break;
                    }
                    printApidDb(ar.apid(), start, stop, cur);
                }
            }
        }
    }

    public List<Short> getApids() throws RocksDBException {
        List<Short> apids = new ArrayList<>();
        try (RocksIterator cur = tablespace.getRdb().newIterator()) {
            Record ar;
            short apid = 0;
            while (true) {
                cur.seek(Record.key(tbsIndex, apid, Long.MAX_VALUE, Short.MAX_VALUE));
                ar = new Record(cur.key(), cur.value());
                apid = ar.apid();
                if (apid == Short.MAX_VALUE) {
                    break;
                }
                apids.add(apid);
            }
        }
        return apids;
    }

    class CcsdsIndexIteratorAdapter implements IndexIterator {
        CcsdsIndexIterator iterator;
        final Set<Short> apids;

        CcsdsIndexIteratorAdapter(Set<Short> apids, long start, long stop) {
            this.apids = apids;
            iterator = new CcsdsIndexIterator((short) -1, start, stop);
        }

        @Override
        public void close() {
            iterator.close();
        }

        @Override
        public ArchiveRecord getNextRecord() {
            while (true) {
                Record r = iterator.getNextRecord();
                if (r == null) {
                    return null;
                }

                short apid = r.apid;
                if ((apids == null) || (apids.contains(apid))) {
                    String pn = "apid_" + apid;
                    NamedObjectId id = NamedObjectId.newBuilder().setName(pn).build();
                    ArchiveRecord.Builder arb = ArchiveRecord.newBuilder().setId(id).setNum(r.numPackets)
                            .setFirst(TimeEncoding.toProtobufTimestamp(r.firstTime()))
                            .setLast(TimeEncoding.toProtobufTimestamp(r.lastTime))
                            .setSeqFirst(r.seqFirst)
                            .setSeqLast(r.seqLast);
                    return arb.build();
                }
            }
        }
    }

    @NotThreadSafe
    class CcsdsIndexIterator {
        long start, stop;
        AscendingRangeIterator rangeIt;
        short apid, curApid;
        Record curr;

        public CcsdsIndexIterator(short apid, long start, long stop) {
            if (start < 0) {
                start = 0;
            }
            if (stop < 0) {
                stop = Long.MAX_VALUE;
            }
            this.apid = apid;
            this.start = start;
            this.stop = stop;
        }

        // jumps to the beginning of the curApid returning true if there is any record matching the start criteria
        // and false otherwise
        boolean jumpAtApid() throws RocksDBException {
            byte[] kstart = Record.key(tbsIndex, curApid, start, (short) 0);
            byte[] kend = Record.key(tbsIndex, curApid, stop, (short) 0xFFFF);
            if (rangeIt != null) {
                rangeIt.close();
            }
            rangeIt = new AscendingRangeIterator(tablespace.getRdb().newIterator(), kstart, kend);
            return rangeIt.isValid();
        }

        // sets the position of the acur at the beginning of the next apid which matches the start criteria
        boolean nextApid() throws RocksDBException {
            if (curApid == -1) { // init
                if (apid != -1) {
                    curApid = apid;
                    return jumpAtApid();
                } else {
                    curApid = 0;
                }
            }
            if (apid != -1) {
                return false;
            }

            while (true) {
                try (RocksIterator it = tablespace.getRdb().newIterator()) {
                    it.seek(Record.key(tbsIndex, curApid, Long.MAX_VALUE, Short.MAX_VALUE));
                    if (!it.isValid()) {
                        return false;
                    }
                    Record ar = new Record(it.key(), it.value());
                    curApid = ar.apid();
                    if (curApid == Short.MAX_VALUE) {
                        return false;
                    }
                    if (jumpAtApid()) {
                        return true;
                    }
                }
            }
        }

        public Record getNextRecord() {
            if (rangeIt == null || !rangeIt.isValid()) {
                try {
                    if (!nextApid()) {
                        return null;
                    }
                } catch (RocksDBException e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            }

            Record r = new Record(rangeIt.key(), rangeIt.value());
            rangeIt.next();
            return r;
        }

        public void close() {
            if (rangeIt != null) {
                rangeIt.close();
            }
        }
    }

    @Override
    public IndexIterator getIterator(List<NamedObjectId> names, long start, long stop) {
        if (names == null) {
            return new CcsdsIndexIteratorAdapter(null, start, stop);
        } else {
            Set<Short> apids = names.stream()
                    .filter(name -> name.getName().startsWith("apid_"))
                    .map(name -> Short.valueOf(name.getName().substring(5)))
                    .collect(Collectors.toSet());
            return new CcsdsIndexIteratorAdapter(apids, start, stop);
        }
    }

    @Override
    public void streamClosed(Stream stream) {
        log.warn("Stream {} closed", stream.getName());
        streamNames.remove(stream.getName());
        if (streamNames.isEmpty()) {
            // if all the streams we are subscribed to are closed we fail the service
            log.warn("No stream left");
            notifyFailed(new Exception("stream clsed"));
        }
    }

    public synchronized CompletableFuture<Void> rebuild(TimeInterval interval) throws YarchException {
        if (interval.hasStart() || interval.hasEnd()) {
            log.info("{}: Rebuilding ccsds tm index for time interval: {}", yamcsInstance, interval.toStringEncoded());
        } else {
            log.info("{} Rebuilding ccsds tm index", yamcsInstance);
        }

        CompletableFuture<Void> cf = new CompletableFuture<>();

        try {
            deleteRecords(interval);
        } catch (Exception e) {
            log.error("Error when removing existing histograms", e);
            cf.completeExceptionally(e);
            return cf;
        }

        String timeColumnName = StandardTupleDefinitions.GENTIME_COLUMN;
        String streamName = "histo_rebuild_" + streamCounter.incrementAndGet();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            ydb.execute("create stream " + streamName + " as select * from tm "
                    + getWhereCondition(timeColumnName, interval));
        } catch (StreamSqlException | ParseException e) {
            throw new RuntimeException(e);
        }

        Stream stream = ydb.getStream(streamName);
        stream.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                cf.complete(null);
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CcsdsTmIndex.this.onTuple(stream, tuple);
            }
        });
        stream.start();

        return cf;
    }

    private synchronized void deleteRecords(TimeInterval interval) throws RocksDBException {
        YRDB db = tablespace.getRdb();
        try (RocksIterator it = db.newIterator()) {
            it.seekToFirst(); // header
            it.next();
            while (it.isValid()) {
                Record r = new Record(it.key(), it.value());
                if (r.apid == Short.MAX_VALUE) {
                    break;
                }
                byte[] keyStart = Record.key(tbsIndex, r.apid, interval.hasStart() ? interval.getStart() : 0,
                        (short) 0);
                byte[] keyEnd;
                if (interval.hasEnd()) {
                    keyEnd = Record.key(tbsIndex, r.apid, interval.getEnd(), (short) 0);
                } else {
                    keyEnd = Record.key(tbsIndex, r.apid, Long.MAX_VALUE, (short) 0);
                }

                it.seek(keyEnd);
                db.getDb().deleteRange(keyStart, keyEnd);
            }
        }
    }

    public static String getWhereCondition(String timeColumnName, TimeInterval interval) {
        if (!interval.hasStart() && !interval.hasEnd()) {
            return "";
        }
        StringBuilder whereCnd = new StringBuilder();
        whereCnd.append(" where ");
        if (interval.hasStart()) {
            long start = HistogramSegment.GROUPING_FACTOR * (interval.getStart() / HistogramSegment.GROUPING_FACTOR);
            whereCnd.append(timeColumnName + " >= " + start);
            if (interval.hasEnd()) {
                whereCnd.append(" and ");
            }
        }
        if (interval.hasEnd()) {
            long stop = HistogramSegment.GROUPING_FACTOR * (1 + interval.getEnd() / HistogramSegment.GROUPING_FACTOR);
            whereCnd.append(timeColumnName + " < " + stop);
        }

        return whereCnd.toString();
    }

}

class Record {
    long firstTime, lastTime;
    short apid;
    short seqFirst, seqLast;
    int numPackets;
    static final int KEY_SIZE = 16;
    static final int VAL_SIZE = 14;

    public Record(byte[] key, byte[] val) {
        ByteBuffer keyb = ByteBuffer.wrap(key);
        ByteBuffer valb = ByteBuffer.wrap(val);
        keyb.getInt();// tbsIndex
        apid = keyb.getShort();
        firstTime = keyb.getLong();
        seqFirst = keyb.getShort();

        lastTime = valb.getLong();
        seqLast = valb.getShort();
        numPackets = valb.getInt();
    }

    public Record(short apid, long time, short seq, int numPackets) {
        this.apid = apid;
        this.firstTime = time;
        this.lastTime = time;
        this.seqFirst = seq;
        this.seqLast = seq;
        this.numPackets = numPackets;
    }

    static byte[] key(int tbsIndex, short apid, long start, short seqFirst) {
        ByteBuffer bbk = ByteBuffer.allocate(KEY_SIZE);
        bbk.putInt(tbsIndex);
        bbk.putShort(apid);
        bbk.putLong(start);
        bbk.putShort(seqFirst);

        return bbk.array();
    }

    public long firstTime() {
        return firstTime;
    }

    public long lastTime() {
        return lastTime;
    }

    public short apid() {
        return apid;
    }

    public short firstSeq() {
        return seqFirst;
    }

    public short lastSeq() {
        return seqLast;
    }

    byte[] key(int tbsIndex) {
        return key(tbsIndex, apid, firstTime, seqFirst);
    }

    byte[] val() {
        ByteBuffer bbv = ByteBuffer.allocate(VAL_SIZE);
        bbv.putLong(lastTime);
        bbv.putShort(seqLast);
        bbv.putInt(numPackets);
        return bbv.array();
    }

    @Override
    public String toString() {
        return "apid=" + apid() + " time: (" + firstTime + "," + lastTime + ") seq:(" + firstSeq() + "," + lastSeq()
                + ")";
    }

    public int numPackets() {
        return numPackets;
    }
}
