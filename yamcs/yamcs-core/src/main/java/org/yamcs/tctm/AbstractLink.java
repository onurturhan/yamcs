package org.yamcs.tctm;

import static org.yamcs.parameter.SystemParametersService.getPV;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.Parameter;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Abstract link implementation as a {@link Service} handling the basic enable/disable getConfig operations
 * 
 * @author nm
 *
 */
public abstract class AbstractLink extends AbstractService implements Link, SystemParametersProducer {
    protected String yamcsInstance;
    protected String linkName;
    protected Log log;
    protected EventProducer eventProducer;
    protected YConfiguration config;
    protected AtomicBoolean disabled = new AtomicBoolean(false);
    private Parameter spLinkStatus, spDataOutCount, spDataInCount;
    protected TimeService timeService;

    /**
     * singleton for netty worker group. In the future we may have an option to create different worker groups for
     * different links but for now we stick to one.
     */
    static NioEventLoopGroup nelg = new NioEventLoopGroup();

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        this.yamcsInstance = instance;
        this.linkName = name;
        this.config = config;
        log = new Log(getClass(), instance);
        log.setContext(name);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, name, 10000);
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return linkName;
    }

    @Override
    public Status getLinkStatus() {
        if (isDisabled()) {
            return Status.DISABLED;
        }
        if (state() == State.FAILED) {
            return Status.FAILED;
        }

        return connectionStatus();
    }

    @Override
    public String getDetailedStatus() {
        return "";
    }

    protected static NioEventLoopGroup getEventLoop() {
        return nelg;
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        boolean b = disabled.getAndSet(false);
        if (b) {
            try {
                doEnable();
            } catch (Exception e) {
                disabled.set(true);
                log.warn("Failed to enable link", e);
            }
        }
    }

    @Override
    public void disable() {
        boolean b = disabled.getAndSet(true);
        if (!b) {
            try {
                doDisable();
            } catch (Exception e) {
                disabled.set(false);
                log.warn("Failed to disable link", e);
            }
        }
    }

    @Override
    public boolean isDisabled() {
        return disabled.get();
    }

    public boolean isRunningAndEnabled() {
        State state = state();
        return (state == State.RUNNING || state == State.STARTING) && !disabled.get();
    }

    protected void doDisable() throws Exception {
    }

    protected void doEnable() throws Exception {
    }

    /**
     * In case the link should be connected (i.e. is running and enabled) this method is called to return the actual
     * connection status
     */
    protected abstract Status connectionStatus();

    protected long getCurrentTime() {
        return timeService.getMissionTime();
    }

    @Override
    public void setupSystemParameters(SystemParametersService sysParamCollector) {
        spLinkStatus = sysParamCollector.createEnumeratedSystemParameter(linkName + "/linkStatus", Status.class,
                "The current status of this link");
        EnumeratedParameterType spLinkStatusType = (EnumeratedParameterType) spLinkStatus.getParameterType();
        spLinkStatusType.enumValue(Status.OK.name())
                .setDescription("This link is up and ready to receive (or send) data");
        spLinkStatusType.enumValue(Status.UNAVAIL.name()).setDescription("This link is down unexpectedly");
        spLinkStatusType.enumValue(Status.DISABLED.name()).setDescription("This link was disabled by a user");
        spLinkStatusType.enumValue(Status.FAILED.name())
                .setDescription("An internal error occurred while processing data");

        spDataOutCount = sysParamCollector.createSystemParameter(linkName + "/dataOutCount", Type.UINT64,
                "The total number of items (e.g. telecommand packets) that have been sent through this link");
        spDataInCount = sysParamCollector.createSystemParameter(linkName + "/dataInCount", Type.UINT64,
                "The total number of items (e.g. telemetry packets) that have been received through this link");
    }

    @Override
    public List<ParameterValue> getSystemParameters() {
        long time = getCurrentTime();

        ArrayList<ParameterValue> list = new ArrayList<>();
        try {
            collectSystemParameters(time, list);
        } catch (Exception e) {
            log.error("Exception caught when collecting link system parameters", e);
        }
        return list;
    }

    /**
     * adds system parameters link status and data in/out to the list.
     * <p>
     * The inheriting classes should call super.collectSystemParameters and then add their own parameters to the list
     * 
     * @param time
     * @param list
     */
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        list.add(getPV(spLinkStatus, time, getLinkStatus()));
        list.add(getPV(spDataOutCount, time, getDataOutCount()));
        list.add(getPV(spDataInCount, time, getDataInCount()));
    }

}
