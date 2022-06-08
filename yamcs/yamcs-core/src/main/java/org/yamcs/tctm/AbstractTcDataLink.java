package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent;

import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

/**
 * Base implementation for a TC data link that initialises a post processor and implements basic methods.
 * 
 * 
 * @author nm
 *
 */
public abstract class AbstractTcDataLink extends AbstractLink implements TcDataLink {

    protected CommandHistoryPublisher commandHistoryPublisher;

    protected AtomicLong dataCount = new AtomicLong();

    protected CommandPostprocessor cmdPostProcessor;
    static final PreparedCommand SIGNAL_QUIT = new PreparedCommand(new byte[0]);

    protected long housekeepingInterval = 10000;
    private AggregatedDataLink parent = null;

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration config) throws ConfigurationException {
        super.init(yamcsInstance, linkName, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);

        initPostprocessor(yamcsInstance, config);
    }

    @Override
    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.getWallclockTime();
        }
    }

    protected void initPostprocessor(String instance, YConfiguration config) {
        String commandPostprocessorClassName = GenericCommandPostprocessor.class.getName();
        YConfiguration commandPostprocessorArgs = null;

        if (config != null) {
            commandPostprocessorClassName = config.getString("commandPostprocessorClassName",
                    GenericCommandPostprocessor.class.getName());
            if (config.containsKey("commandPostprocessorArgs")) {
                commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
            }
        }

        try {
            if (commandPostprocessorArgs != null) {
                cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance,
                        commandPostprocessorArgs);
            } else {
                cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the command postprocessor", e);
            throw e;
        }
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
    }

    /**
     * Postprocesses the command, unless postprocessing is disabled.
     * 
     * @return potentially modified binary, or {@code null} to indicate that the command should not be handled further.
     */
    protected byte[] postprocess(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (!pc.disablePostprocessing()) {
            binary = cmdPostProcessor.process(pc);
            if (binary == null) {
                log.warn("command postprocessor did not process the command");
            }
        }
        return binary;
    }

    @Override
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
        return dataCount.get();
    }

    @Override
    public void resetCounters() {
        dataCount.set(0);
    }

    @Override
    public AggregatedDataLink getParent() {
        return parent;
    }

    @Override
    public void setParent(AggregatedDataLink parent) {
        this.parent = parent;
    }

    /** Send to command history the failed command */
    protected void failedCommand(CommandId commandId, String reason) {
        log.debug("Failing command {}: {}", commandId, reason);
        long currentTime = getCurrentTime();
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent,
                currentTime, AckStatus.NOK, reason);
        commandHistoryPublisher.commandFailed(commandId, currentTime, reason);
    }

    /**
     * send an ack in the command history that the command has been sent out of the link
     * 
     * @param commandId
     */
    protected void ackCommand(CommandId commandId) {
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent, getCurrentTime(),
                AckStatus.OK);
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }
}
