package org.yamcs.timeline;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.api.SqlBuilder;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TimelineSourceCapabilities;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ResultListener;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class CommandItemProvider implements ItemProvider {

    private Log log;
    private YarchDatabaseInstance ydb;

    public CommandItemProvider(String yamcsInstance) {
        log = new Log(getClass(), yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);
    }

    @Override
    public TimelineItem getItem(String id) {
        return null;
    }

    @Override
    public void getItems(int limit, String next, ItemFilter filter, ItemListener consumer) {
        try {
            SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
            TimeInterval interval = filter.getTimeInterval();
            if (interval.hasEnd()) {
                sqlb.where("gentime < ?", interval.getEnd());
            }
            if (interval.hasStart()) {
                sqlb.where("gentime >= ?", interval.getStart());
            }
            sqlb.limit(limit + 1);

            StreamSqlStatement stmt = ydb.createStatement(sqlb.toString(),
                    sqlb.getQueryArguments().toArray());
            ydb.execute(stmt, new ResultListener() {

                @Override
                public void next(Tuple tuple) {
                    consumer.next(toItem(tuple));
                }

                @Override
                public void completeExceptionally(Throwable t) {
                    consumer.completeExceptionally(t);
                }

                @Override
                public void complete() {
                    consumer.complete(null);
                }
            });
        } catch (StreamSqlException | ParseException e) {
            log.error("Exception when executing query", e);
        }
    }

    @Override
    public TimelineItem addItem(TimelineItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineItem updateItem(TimelineItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineItem deleteItem(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineItem deleteTimelineGroup(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getTags() {
        return Collections.emptyList();
    }

    @Override
    public TimelineSourceCapabilities getCapabilities() {
        return TimelineSourceCapabilities.newBuilder()
                .setReadOnly(true)
                .build();
    }

    private static TimelineEvent toItem(Tuple tuple) {
        long gentime = (Long) tuple.getColumn(PreparedCommand.CNAME_GENTIME);
        String origin = (String) tuple.getColumn(PreparedCommand.CNAME_ORIGIN);
        int sequenceNumber = (Integer) tuple.getColumn(PreparedCommand.CNAME_SEQNUM);
        String id = gentime + "-" + origin + "-" + sequenceNumber;

        TimelineEvent event = new TimelineEvent(id);
        event.setStart(gentime);
        event.setName(tuple.getColumn(PreparedCommand.CNAME_CMDNAME));
        return event;
    }
}
