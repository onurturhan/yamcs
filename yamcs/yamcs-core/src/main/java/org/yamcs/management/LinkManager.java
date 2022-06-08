package org.yamcs.management;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.management.LinkManager.InvalidPacketAction.Action;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.links.LinkInfo;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.StreamPbParameterSender;
import org.yamcs.tctm.TcDataLink;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.time.Instant;
import org.yamcs.utils.ServiceUtil;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.Service;
import com.google.gson.Gson;

/**
 * Service that manages all the data links:
 *
 * <ul>
 * <li>is endpoint for the /links API calls</li>
 * <li>for the commanding links it will only send commands if the link is enabled. If no commanding link is enabled, a
 * negative Sent ACK will be produced.</li>
 * <li>TODO: can set exclusive flags - i.e. only one link from a group can be enabled at a time</li>
 * </ul>
 *
 * The configuration of this service is done in the "dataLinks" sections of the yamcs.&lt;instance-name&gt;.yaml file.
 *
 *
 * @author nm
 */
public class LinkManager {
    private Map<String, Link> linksByName = new HashMap<>();

    private YarchDatabaseInstance ydb;
    Log log;
    final String yamcsInstance;
    Set<LinkListener> linkListeners = new CopyOnWriteArraySet<>();
    List<LinkWithInfo> links = new CopyOnWriteArrayList<>();
    final CommandHistoryPublisher cmdHistPublisher;
    Map<Stream, TcStreamSubscriber> tcStreamSubscribers = new HashMap<>();

    public LinkManager(String instanceName) throws InitException {
        this.yamcsInstance = instanceName;
        log = new Log(getClass(), instanceName);
        ydb = YarchDatabase.getInstance(instanceName);
        cmdHistPublisher = new StreamCommandHistoryPublisher(yamcsInstance);

        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        YConfiguration instanceConfig = instance.getConfig();

        if (instanceConfig.containsKey("dataLinks")) {
            try {
                List<YConfiguration> linkConfigs = instanceConfig.getConfigList("dataLinks");
                for (YConfiguration linkConfig : linkConfigs) {
                    createDataLink(linkConfig);
                }
            } catch (IOException e) {
                throw new InitException(e);
            }
        } else {
            log.info("No link created because the section dataLinks was not found");
        }

        YamcsServer.getServer().getThreadPoolExecutor().scheduleAtFixedRate(() -> checkLinkUpdate(), 1, 1,
                TimeUnit.SECONDS);
    }

    private void createDataLink(YConfiguration linkConfig) throws IOException {
        String className = linkConfig.getString("class");
        YConfiguration args = null;
        String linkName = linkConfig.getString("name");
        if (linkConfig.containsKey("args")) {
            args = linkConfig.getConfig("args");
            log.warn(
                    "Deprecation warning: the 'args' parameter in the link {} configuration is deprecated; please move all properties one level up",
                    linkName);
            mergeConfig(linkConfig, args);
        }

        if (linksByName.containsKey(linkName)) {
            throw new ConfigurationException(
                    "Instance " + yamcsInstance + ": there is already a link named '" + linkName + "'");
        }

        Link link = loadLink(className, linkName, linkConfig);
        link.init(yamcsInstance, linkName, linkConfig);

        boolean enabledAtStartup = linkConfig.getBoolean("enabledAtStartup", true);

        if (!enabledAtStartup) {
            link.disable();
        }

        configureDataLink(link, linkConfig);
    }

    // once we don't want to be backwards compatible, we should replace this method by
    // link = YObjectLoader.loadObject(linkClass, yamcsInstance, linkConfig);
    private Link loadLink(String linkClass, String linkName, YConfiguration linkConfig) throws IOException {
        Link link = null;
        try {
            link = YObjectLoader.loadObject(linkClass);
        } catch (ConfigurationException e) {
            // TODO: set this to warn in the next version
            log.info(
                    "Link {} does not have a no-argument constructor. Please add one and implement the initialisation in the init method",
                    linkClass);
            // Ignore for now. Fallback to constructor initialization.
        }

        if (link == null) { // "Legacy" fallback
            link = YObjectLoader.loadObject(linkClass, yamcsInstance, linkName, linkConfig);
        }
        return link;
    }

    private void mergeConfig(YConfiguration linkConfig, YConfiguration args) {
        for (String k : args.getKeys()) {
            if (linkConfig.containsKey(k)) {
                throw new ConfigurationException(linkConfig, "key '" + k
                        + "' present both in link config and args; these two are merged together and this is not allowed");
            }
            linkConfig.getRoot().put(k, args.get(k));
        }
    }

    void configureDataLink(Link link, YConfiguration linkArgs) {
        if (linkArgs == null) {
            linkArgs = YConfiguration.emptyConfig();
        }
        // this is the old configuration where each link was configured one stream
        Stream singleStream = getStream(linkArgs, "stream");

        // this is the new configuration where one can specify one of each stream for a link
        // such that we can have links doing both TC and TM
        Stream tcStream = getStream(linkArgs, "tcStream");
        Stream tmStream = getStream(linkArgs, "tmStream");
        Stream ppStream = getStream(linkArgs, "ppStream");

        if (link instanceof TmPacketDataLink) {
            Stream streamf = tmStream == null ? singleStream : tmStream;
            if (streamf != null) {
                TmPacketDataLink tmLink = (TmPacketDataLink) link;
                InvalidPacketAction ipa = getInvalidPacketAction(link.getName(), linkArgs);
                tmLink.setTmSink(tmPacket -> processTmPacket(tmLink, tmPacket, streamf, ipa));
            }
        }

        if (link instanceof TcDataLink) {
            TcDataLink tcLink = (TcDataLink) link;
            Stream stream = tcStream == null ? singleStream : tcStream;

            if (stream != null) {
                TcStreamSubscriber tcs = tcStreamSubscribers.get(stream);
                if (tcs == null) {
                    tcs = new TcStreamSubscriber(true);
                    tcStreamSubscribers.put(stream, tcs);
                    stream.addSubscriber(tcs);
                }
                tcs.addLink(tcLink);
            }
            tcLink.setCommandHistoryPublisher(cmdHistPublisher);
        }

        if (link instanceof ParameterDataLink) {
            Stream stream = ppStream == null ? singleStream : ppStream;
            if (stream != null) {
                ((ParameterDataLink) link).setParameterSink(new StreamPbParameterSender(yamcsInstance, stream));
            }
        }

        if (link instanceof AggregatedDataLink) {
            for (Link l : ((AggregatedDataLink) link).getSubLinks()) {
                configureDataLink(l, l.getConfig());
            }
        }

        linksByName.put(link.getName(), link);
        String json = new Gson().toJson(linkArgs.toMap());
        registerLink(link.getName(), json, link);
    }

    Stream getStream(YConfiguration linkArgs, String configKey) {
        Stream stream = null;
        String streamName = linkArgs.getString(configKey, null);
        if (streamName != null) {
            stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
        }
        return stream;
    }

    private void processTmPacket(TmPacketDataLink tmLink, TmPacket tmPacket, Stream stream, InvalidPacketAction ipa) {
        if (tmPacket.isInvalid()) {
            if (ipa.action == Action.DROP) {
                return;
            } else if (ipa.action == Action.DIVERT) {
                Tuple t = new Tuple(StandardTupleDefinitions.INVALID_TM,
                        new Object[] { tmPacket.getReceptionTime(), ipa.divertStream.getDataCount(),
                                tmPacket.getPacket() });
                ipa.divertStream.emitTuple(t);
                return;
            } // if action is PROCESS, continue below
        }

        Instant ertime = tmPacket.getEarthReceptionTime();
        Tuple t = null;
        if (ertime == Instant.INVALID_INSTANT) {
            ertime = null;
        }
        Long obt = tmPacket.getObt() == Long.MIN_VALUE ? null : tmPacket.getObt();
        t = new Tuple(StandardTupleDefinitions.TM,
                new Object[] { tmPacket.getGenerationTime(), tmPacket.getSeqCount(), tmPacket.getReceptionTime(),
                        tmPacket.getStatus(), tmPacket.getPacket(), ertime, obt, tmLink.getName() });
        stream.emitTuple(t);

    }

    private InvalidPacketAction getInvalidPacketAction(String linkName, YConfiguration linkArgs) {
        InvalidPacketAction ipa = new InvalidPacketAction();
        if (linkArgs.containsKey("invalidPackets")) {
            ipa.action = linkArgs.getEnum("invalidPackets", Action.class);
            if (ipa.action == Action.DIVERT) {
                String divertStream = linkArgs.getString("invalidPacketsStream", "invalid_tm");
                ipa.divertStream = ydb.getStream(divertStream);
                if (ipa.divertStream == null) {
                    throw new ConfigurationException("Cannot find stream '" + divertStream
                            + "' (required if invalidPackets: DIVERT has been specified)");
                }
            }
        } else if (linkArgs.containsKey("dropCorruptedPackets")) {
            log.warn("Please repace dropCorruptedPackets with 'invalidPackets: DROP' into " + linkName
                    + " configuration");
            ipa.action = linkArgs.getBoolean("dropCorruptedPackets") ? Action.DROP : Action.PROCESS;
        } else {
            ipa.action = Action.DROP;
        }

        return ipa;
    }

    public void startLinks() {
        SystemParametersService collector = SystemParametersService.getInstance(yamcsInstance);

        if (collector != null) {
            linksByName.forEach((name, link) -> {
                if (link instanceof SystemParametersProducer) {
                    link.setupSystemParameters(collector);
                    collector.registerProducer((SystemParametersProducer) link);
                }
            });
        }

        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                log.debug("Starting service link {}", name);
                ((Service) link).startAsync();
            }
        });
        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                ServiceUtil.awaitServiceRunning((Service) link);
            }
        });
    }

    public void stopLinks() {
        linksByName.forEach((name, link) -> {
            unregisterLink(name);
            if (link instanceof Service) {
                ((Service) link).stopAsync();
            }
        });
        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                log.debug("Awaiting termination of link {}", link.getName());
                ServiceUtil.awaitServiceTerminated((Service) link, YamcsServer.SERVICE_STOP_GRACE_TIME, log);
            }
        });
    }

    private void checkLinkUpdate() {
        // see if any link has changed
        for (LinkWithInfo lwi : links) {
            if (lwi.hasChanged()) {
                LinkInfo li = lwi.linkInfo;
                linkListeners.forEach(l -> l.linkChanged(li));
            }
        }
    }

    private void registerLink(String linkName, String spec, Link link) {
        LinkInfo.Builder linkb = LinkInfo.newBuilder().setInstance(yamcsInstance)
                .setName(linkName)
                .setDisabled(link.isDisabled())
                .setStatus(link.getLinkStatus().name())
                .setType(link.getClass().getName())
                .setSpec(spec)
                .setDataInCount(link.getDataInCount())
                .setDataOutCount(link.getDataOutCount());
        if (link.getDetailedStatus() != null) {
            linkb.setDetailedStatus(link.getDetailedStatus());
        }
        Link parent = link.getParent();
        if (parent != null) {
            linkb.setParentName(parent.getName());
        }
        LinkInfo linkInfo = linkb.build();
        links.add(new LinkWithInfo(link, linkInfo));
        linkListeners.forEach(l -> l.linkRegistered(linkInfo));
    }

    private void unregisterLink(String linkName) {
        Optional<LinkWithInfo> o = getLinkWithInfo(linkName);
        if (o.isPresent()) {
            LinkWithInfo lwi = o.get();
            links.remove(lwi);
            linkListeners.forEach(l -> l.linkUnregistered(lwi.linkInfo));
        }
    }

    public Optional<LinkWithInfo> getLinkWithInfo(String linkName) {
        return links.stream()
                .filter(lwi -> linkName.equals(lwi.linkInfo.getName()))
                .findFirst();
    }

    /**
     * Adds a listener that is to be notified when any processor, or any client is updated. Calling this multiple times
     * has no extra effects. Either you listen, or you don't.
     */
    public boolean addLinkListener(LinkListener l) {
        return linkListeners.add(l);
    }

    public void enableLink(String linkName) {
        log.debug("received enableLink for {}", linkName);
        checkAndGetLink(linkName).enable();
    }

    public void disableLink(String linkName) {
        log.debug("received disableLink for {}", linkName);
        checkAndGetLink(linkName).disable();
    }

    public void resetCounters(String linkName) {
        log.debug("received resetCounters for {}", linkName);
        checkAndGetLink(linkName).resetCounters();
    }

    private Link checkAndGetLink(String linkName) {
        Link link = getLink(linkName);
        if (link == null) {
            throw new IllegalArgumentException(
                    "There is no link named '" + linkName + "' in instance " + yamcsInstance);
        }
        return link;
    }

    public boolean removeLinkListener(LinkListener l) {
        return linkListeners.remove(l);
    }

    public List<LinkInfo> getLinkInfo() {
        return links.stream().map(lwi -> lwi.linkInfo).collect(Collectors.toList());
    }

    public LinkInfo getLinkInfo(String linkName) {
        Optional<LinkInfo> o = links.stream()
                .map(lwi -> lwi.linkInfo)
                .filter(li -> li.getName().equals(linkName))
                .findFirst();
        if (o.isPresent()) {
            return o.get();
        } else {
            return null;
        }
    }

    public List<Link> getLinks() {
        return new ArrayList<>(linksByName.values());
    }

    /**
     * Return the link by the given name or null if there is no such link.
     */
    public Link getLink(String linkName) {
        return linksByName.get(linkName);
    }

    /**
     * What to do with invalid packets.
     */
    static class InvalidPacketAction {
        enum Action {
            /**
             * Do nothing
             */
            DROP,

            /**
             * Send packets on the normal TM stream
             */
            PROCESS,

            /**
             * Send packets on an alternate stream
             */
            DIVERT
        };

        Stream divertStream;
        Action action;
    }

    public class LinkWithInfo {
        final Link link;
        LinkInfo linkInfo;

        public LinkWithInfo(Link link, LinkInfo linkInfo) {
            this.link = link;
            this.linkInfo = linkInfo;
        }

        boolean hasChanged() {
            try {
                String prevDetailedStatus = linkInfo.hasDetailedStatus() ? linkInfo.getDetailedStatus() : null;
                if (!linkInfo.getStatus().equals(link.getLinkStatus().name())
                        || linkInfo.getDisabled() != link.isDisabled()
                        || linkInfo.getDataInCount() != link.getDataInCount()
                        || linkInfo.getDataOutCount() != link.getDataOutCount()
                        || !Objects.equals(prevDetailedStatus, link.getDetailedStatus())) {

                    LinkInfo.Builder lib = LinkInfo.newBuilder(linkInfo)
                            .setDisabled(link.isDisabled())
                            .setStatus(link.getLinkStatus().name())
                            .setDataInCount(link.getDataInCount())
                            .setDataOutCount(link.getDataOutCount());
                    String ds = link.getDetailedStatus();
                    if (ds != null) {
                        lib.setDetailedStatus(ds);
                    }
                    linkInfo = lib.build();
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                log.error("Error checking link status for {}", link.getName(), e);
                return false;
            }
        }

        public Link getLink() {
            return link;
        }
    }

    class TcStreamSubscriber implements StreamSubscriber {
        final List<TcDataLink> tcLinks = new ArrayList<>();
        final boolean failIfNoLinkAvailable;

        public TcStreamSubscriber(boolean failIfNoLinkAvailable) {
            this.failIfNoLinkAvailable = failIfNoLinkAvailable;
        }

        void addLink(TcDataLink tcLink) {
            tcLinks.add(tcLink);
        }

        @Override
        public void onTuple(Stream s, Tuple tuple) {
            XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
            PreparedCommand pc = PreparedCommand.fromTuple(tuple, xtcedb);
            boolean sent = false;
            String reason = "no link available";
            for (TcDataLink tcLink : tcLinks) {
                if (!tcLink.isEffectivelyDisabled()) {
                    try {
                        if (tcLink.sendCommand(pc)) {
                            sent = true;
                            break;
                        }
                    } catch (Exception e) {
                        log.error("Error sending command via link {}", tcLink, e);
                        reason = "Error sending command via " + tcLink.getName() + ": " + e.getMessage();
                    }
                }
            }

            if (!sent && failIfNoLinkAvailable) {
                CommandId commandId = pc.getCommandId();
                log.info("Failing command stream: {}, cmdId: {}, reason: {}", s.getName(), pc.getCommandId(), reason);
                long currentTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();
                cmdHistPublisher.publishAck(commandId, AcknowledgeSent,
                        currentTime, AckStatus.NOK, reason);
                cmdHistPublisher.commandFailed(commandId, currentTime, reason);
            }
        }

        @Override
        public void streamClosed(Stream s) {
            log.debug("Stream {} closed", s.getName());
        }
    }

}
