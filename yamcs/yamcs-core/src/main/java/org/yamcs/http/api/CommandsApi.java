package org.yamcs.http.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.cmdhistory.Attribute;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.MediaType;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.protobuf.AbstractCommandsApi;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.ExportCommandRequest;
import org.yamcs.protobuf.GetCommandRequest;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.ListCommandsRequest;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.StreamCommandsRequest;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yaml.snakeyaml.util.UriEncoder;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class CommandsApi extends AbstractCommandsApi<Context> {

    private static final Pattern PATTERN_COMMAND_ID = Pattern.compile("([0-9]+)(-(.*))?-([0-9]+)");
    private static final Log log = new Log(CommandsApi.class);

    @Override
    public void issueCommand(Context ctx, IssueCommandRequest request, Observer<IssueCommandResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        String requestCommandName = UriEncoder.decode(request.getName());
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        MetaCommand cmd = MdbApi.verifyCommand(mdb, requestCommandName);

        ctx.checkObjectPrivileges(ObjectPrivilegeType.Command, cmd.getQualifiedName());

        String origin = ctx.getClientAddress();
        int sequenceNumber = 0;
        boolean dryRun = false;
        String comment = null;

        if (request.hasOrigin()) { // TODO remove this override?
            origin = request.getOrigin();
        }
        if (request.hasDryRun()) {
            dryRun = request.getDryRun();
        }
        if (request.hasSequenceNumber()) {
            sequenceNumber = request.getSequenceNumber();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }

        Map<String, Object> assignments = new LinkedHashMap<>();
        if (request.getAssignmentCount() > 0) {
            for (Assignment a : request.getAssignmentList()) {
                assignments.put(a.getName(), a.getValue());
            }
        } else if (request.hasArgs()) {
            assignments.putAll(GpbWellKnownHelper.toJava(request.getArgs()));
        }

        // Prepare the command
        PreparedCommand preparedCommand;
        try {
            preparedCommand = processor.getCommandingManager().buildCommand(cmd, assignments, origin, sequenceNumber,
                    ctx.user);
            if (comment != null && !comment.trim().isEmpty()) {
                preparedCommand.setComment(comment);
            }

            if (request.getExtraCount() > 0) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                request.getExtraMap().forEach((k, v) -> {
                    if (!YamcsServer.getServer().hasCommandOption(k)) {
                        throw new BadRequestException("Unknown command option '" + k + "'");
                    }
                    preparedCommand.addAttribute(CommandHistoryAttribute.newBuilder()
                            .setName(k).setValue(v).build());
                });
            }

            if (request.hasDisableVerifiers()) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                preparedCommand.disableCommandVerifiers(request.getDisableVerifiers());
            }

            if (request.hasDisableTransmissionConstraints()) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                preparedCommand.disableTransmissionConstraints(request.getDisableTransmissionConstraints());
            } else if (request.getVerifierConfigCount() > 0) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                List<String> invalidVerifiers = new ArrayList<>();
                for (String stage : request.getVerifierConfigMap().keySet()) {
                    if (!hasVerifier(cmd, stage)) {
                        invalidVerifiers.add(stage);
                    }
                }
                if (!invalidVerifiers.isEmpty()) {
                    throw new BadRequestException(
                            "The command does not have the following verifiers: " + invalidVerifiers.toString());
                }

                request.getVerifierConfigMap().forEach((k, v) -> {
                    preparedCommand.addVerifierConfig(k, v);
                });
            }

            // make the source - should perhaps come from the client
            StringBuilder sb = new StringBuilder();
            sb.append(cmd.getQualifiedName());
            sb.append("(");
            boolean first = true;
            for (Entry<String, Object> assignment : assignments.entrySet()) {
                Argument a = preparedCommand.getMetaCommand().getArgument(assignment.getKey());
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(assignment.getKey()).append(": ");

                boolean needDelimiter = a != null && (a.getArgumentType() instanceof StringArgumentType
                        || a.getArgumentType() instanceof EnumeratedArgumentType);
                if (needDelimiter) {
                    sb.append("\"");
                }
                sb.append(assignment.getValue());
                if (needDelimiter) {
                    sb.append("\"");
                }
            }
            sb.append(")");
            preparedCommand.setSource(sb.toString());

        } catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        } catch (ErrorInCommand e) {
            throw new BadRequestException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }

        if (!dryRun && processor.getConfig().checkCommandClearance()) {
            if (ctx.user.getClearance() == null) {
                throw new ForbiddenException("Not cleared for commanding");
            }
            Levels clearance = Levels.valueOf(ctx.user.getClearance().getLevel().toUpperCase());
            Levels level = null;
            if (preparedCommand.getMetaCommand().getEffectiveDefaultSignificance() != null) {
                level = preparedCommand.getMetaCommand().getEffectiveDefaultSignificance().getConsequenceLevel();
            }
            if (level != null && level.isMoreSevere(clearance)) {
                throw new ForbiddenException("Not cleared for this level of commands");
            }
        }

        // Good, now send
        CommandQueue queue;
        if (dryRun) {
            CommandQueueManager mgr = processor.getCommandingManager().getCommandQueueManager();
            queue = mgr.getQueue(ctx.user, preparedCommand);
        } else {
            queue = processor.getCommandingManager().sendCommand(ctx.user, preparedCommand);
        }

        IssueCommandResponse.Builder responseb = IssueCommandResponse.newBuilder()
                .setId(toStringIdentifier(preparedCommand.getCommandId()))
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(preparedCommand.getGenerationTime()))
                .setOrigin(preparedCommand.getCommandId().getOrigin())
                .setSequenceNumber(preparedCommand.getCommandId().getSequenceNumber())
                .setCommandName(preparedCommand.getMetaCommand().getQualifiedName())
                .setSource(preparedCommand.getSource())
                .setUsername(preparedCommand.getUsername())
                .addAllAssignments(preparedCommand.getAssignments());

        byte[] unprocessedBinary = preparedCommand.getUnprocessedBinary();
        if (unprocessedBinary != null) {
            responseb.setUnprocessedBinary(ByteString.copyFrom(unprocessedBinary));
        }

        byte[] binary = preparedCommand.getBinary();
        if (binary != null) {
            responseb.setBinary(ByteString.copyFrom(binary));
        }

        if (queue != null) {
            responseb.setQueue(queue.getName());
        }

        observer.complete(responseb.build());
    }

    private boolean hasVerifier(MetaCommand cmd, String stage) {
        boolean hasVerifier = cmd.getCommandVerifiers().stream().anyMatch(cv -> cv.getStage().equals(stage));
        if (hasVerifier) {
            return true;
        } else {
            MetaCommand parent = cmd.getBaseMetaCommand();
            if (parent == null) {
                return false;
            } else {
                return hasVerifier(parent, stage);
            }
        }
    }

    @Override
    public void updateCommandHistory(Context ctx, UpdateCommandHistoryRequest request, Observer<Empty> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }
        if (!ctx.user.hasSystemPrivilege(SystemPrivilege.ModifyCommandHistory)) {
            throw new ForbiddenException("User has no privilege to update command history");
        }

        CommandId cmdId = fromStringIdentifier(request.getName(), request.getId());
        CommandingManager manager = processor.getCommandingManager();
        for (CommandHistoryAttribute attr : request.getAttributesList()) {
            manager.setCommandAttribute(cmdId, attr);
        }

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void listCommands(Context ctx, ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            observer.complete(ListCommandsResponse.getDefaultInstance());
            return;
        }

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        if (request.hasPos()) {
            log.warn("DEPRECATION WARNING: Do not use pos, use continuationToken instead");
        }

        CommandPageToken nextToken = null;
        if (request.hasNext()) {
            String next = request.getNext();
            nextToken = CommandPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }
        if (request.hasQueue()) {
            sqlb.where("queue = ?", request.getQueue());
        }
        if (request.hasQ()) {
            sqlb.where("cmdName like ?", "%" + request.getQ() + "%");
        }
        if (nextToken != null) {
            // TODO this currently ignores the origin column (also part of the key)
            // Requires string comparison in StreamSQL, and an even more complicated query condition...
            if (desc) {
                sqlb.where("(gentime < ? or (gentime = ? and seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime > ? or (gentime = ? and seqNum > ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            }
        }

        sqlb.descend(desc);

        // TODO: remove, not correct with permission filter below
        sqlb.limit(pos, limit + 1l); // one more to detect hasMore

        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            CommandHistoryEntry last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory,
                        entry.getCommandName())) {
                    count++;
                    if (count <= limit) {
                        responseb.addEntry(entry);
                        last = entry;
                    } else {
                        stream.close();
                    }
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    CommandId cmdId = last.getCommandId();
                    CommandPageToken token = new CommandPageToken(
                            cmdId.getGenerationTime(), last.getOrigin(),
                            last.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getCommand(Context ctx, GetCommandRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        Matcher matcher = PATTERN_COMMAND_ID.matcher(request.getId());
        if (!matcher.matches()) {
            throw new BadRequestException("Invalid command id");
        }

        long gentime = Long.parseLong(matcher.group(1));
        String origin = matcher.group(3) != null ? matcher.group(3) : "";
        int seqNum = Integer.parseInt(matcher.group(4));

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum)
                .where("origin = ?", origin);
        List<CommandHistoryEntry> commands = new ArrayList<>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry command = GPBHelper.tupleToCommandHistoryEntry(tuple);
                commands.add(command);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    observer.completeExceptionally(new NotFoundException());
                } else if (commands.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    CommandHistoryEntry command = commands.get(0);
                    ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, command.getCommandName());
                    observer.complete(command);
                }
            }
        });
    }

    @Override
    public void exportCommand(Context ctx, ExportCommandRequest request, Observer<HttpBody> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        Matcher matcher = PATTERN_COMMAND_ID.matcher(request.getId());
        if (!matcher.matches()) {
            throw new BadRequestException("Invalid command id");
        }

        long gentime = Long.parseLong(matcher.group(1));
        String origin = matcher.group(3) != null ? matcher.group(3) : "";
        int seqNum = Integer.parseInt(matcher.group(4));

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum)
                .where("origin = ?", origin);

        List<CommandHistoryEntry> commands = new ArrayList<>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry command = GPBHelper.tupleToCommandHistoryEntry(tuple);
                commands.add(command);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    observer.completeExceptionally(new NotFoundException());
                } else if (commands.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    CommandHistoryEntry command = commands.get(0);
                    ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, command.getCommandName());

                    String timestamp = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()
                            .truncatedTo(ChronoUnit.MILLIS))
                            .replace("-", "")
                            .replace(":", "")
                            .replace(".", "");
                    HttpBody.Builder responseb = HttpBody.newBuilder()
                            .setFilename("command-" + timestamp + "-" + seqNum + ".raw")
                            .setContentType(MediaType.OCTET_STREAM.toString());
                    for (CommandHistoryAttribute attr : command.getAttrList()) {
                        if (attr.getName().equals(PreparedCommand.CNAME_BINARY)) {
                            responseb.setData(attr.getValue().getBinaryValue());
                        }
                    }
                    observer.complete(responseb.build());
                }
            }
        });
    }

    @Override
    public void subscribeCommands(Context ctx, SubscribeCommandsRequest request,
            Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        Processor processor = ProcessingApi.verifyProcessor(instance, request.getProcessor());
        if (!processor.hasCommanding() || processor.getCommandHistoryManager() == null) {
            return; // No Error, just send no data
        }

        CommandHistoryRequestManager requestManager = processor.getCommandHistoryManager();
        boolean ignorePastCommands = true;
        if (request.hasIgnorePastCommands()) {
            ignorePastCommands = request.getIgnorePastCommands();
        }

        long since = ignorePastCommands ? processor.getCurrentTime() : 0;
        CommandHistoryConsumer listener = new CommandHistoryConsumer() {

            @Override
            public void addedCommand(PreparedCommand pc) {
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, pc.getCommandName())) {
                    CommandHistoryEntry entry = CommandHistoryEntry.newBuilder()
                            .setId(pc.getId())
                            .setOrigin(pc.getOrigin())
                            .setCommandName(pc.getCommandName())
                            .setSequenceNumber(pc.getSequenceNumber())
                            .setCommandId(pc.getCommandId())
                            .setGenerationTime(TimeEncoding.toProtobufTimestamp(pc.getCommandId().getGenerationTime()))
                            .addAllAssignments(pc.getAssignments())
                            .addAllAttr(pc.getAttributes())
                            .build();
                    observer.next(entry);
                }
            }

            @Override
            public void updatedCommand(CommandId cmdId, long changeDate, List<Attribute> attrs) {
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, cmdId.getCommandName())) {
                    CommandHistoryEntry.Builder entry = CommandHistoryEntry.newBuilder()
                            .setId(cmdId.getGenerationTime() + "-" + cmdId.getOrigin() + "-"
                                    + cmdId.getSequenceNumber())
                            .setOrigin(cmdId.getOrigin())
                            .setCommandName(cmdId.getCommandName())
                            .setGenerationTime(TimeEncoding.toProtobufTimestamp(cmdId.getGenerationTime()))
                            .setCommandId(cmdId);
                    for (Attribute a : attrs) {
                        CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder()
                                .setName(a.getKey())
                                .setValue(ValueUtility.toGbp(a.getValue()))
                                .build();
                        entry.addAttr(cha);
                    }
                    observer.next(entry.build());
                }
            }
        };
        CommandHistoryFilter subscription = requestManager.subscribeCommandHistory(null, since, listener);
        observer.setCancelHandler(() -> requestManager.unsubscribeCommandHistory(subscription.subscriptionId));
    }

    @Override
    public void streamCommands(Context ctx, StreamCommandsRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        // Quick-check in case the user is specific
        ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, request.getNameList());

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("cmdName", request.getNameList());
        }

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, entry.getCommandName())) {
                    observer.next(entry);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    private static CommandId fromStringIdentifier(String commandName, String id) {
        CommandId.Builder b = CommandId.newBuilder();
        b.setCommandName(commandName);
        int firstDash = id.indexOf('-');
        long generationTime = Long.parseLong(id.substring(0, firstDash));
        b.setGenerationTime(generationTime);
        int lastDash = id.lastIndexOf('-');
        int sequenceNumber = Integer.parseInt(id.substring(lastDash + 1));
        b.setSequenceNumber(sequenceNumber);
        if (firstDash != lastDash) {
            String origin = id.substring(firstDash + 1, lastDash);
            b.setOrigin(origin);
        } else {
            b.setOrigin("");
        }

        return b.build();
    }

    private static String toStringIdentifier(CommandId commandId) {
        String id = commandId.getGenerationTime() + "-";
        if (commandId.hasOrigin() && !"".equals(commandId.getOrigin())) {
            id += commandId.getOrigin() + "-";
        }
        return id + commandId.getSequenceNumber();
    }

    /**
     * Stateless continuation token for paged requests on the cmdhist table
     */
    private static class CommandPageToken {

        long gentime;
        String origin;
        int seqNum;

        CommandPageToken(long gentime, String origin, int seqNum) {
            this.gentime = gentime;
            this.origin = origin;
            this.seqNum = seqNum;
        }

        static CommandPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, CommandPageToken.class);
        }

        String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }
}
