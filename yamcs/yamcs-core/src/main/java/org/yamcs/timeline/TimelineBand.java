package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineBandDb.CNAME_DESCRIPTION;
import static org.yamcs.timeline.TimelineBandDb.CNAME_ID;
import static org.yamcs.timeline.TimelineBandDb.CNAME_NAME;
import static org.yamcs.timeline.TimelineBandDb.CNAME_SHARED;
import static org.yamcs.timeline.TimelineBandDb.CNAME_TAGS;
import static org.yamcs.timeline.TimelineBandDb.CNAME_TYPE;
import static org.yamcs.timeline.TimelineBandDb.CNAME_USERNAME;
import static org.yamcs.timeline.TimelineBandDb.PROP_PREFIX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.yamcs.protobuf.TimelineBandType;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class TimelineBand {

    private final UUID id;

    private String name;
    private String description;
    private TimelineBandType type;
    private boolean shared;
    private String username;
    private List<String> tags = new ArrayList<>();
    private Map<String, String> properties = new HashMap<>();

    public TimelineBand(UUID id) {
        this.id = id;
    }

    TimelineBand(Tuple tuple) {
        id = tuple.getColumn(CNAME_ID);
        name = tuple.getColumn(CNAME_NAME);
        description = tuple.getColumn(CNAME_DESCRIPTION);
        type = TimelineBandType.valueOf(tuple.<String> getColumn(CNAME_TYPE));
        shared = tuple.getColumn(CNAME_SHARED);
        username = tuple.getColumn(CNAME_USERNAME);

        for (int i = 0; i < tuple.size(); i++) {
            ColumnDefinition column = tuple.getColumnDefinition(i);
            if (column.getName().startsWith(PROP_PREFIX)) {
                String columnName = column.getName().substring(PROP_PREFIX.length());
                properties.put(columnName, tuple.getColumn(column.getName()));
            }
        }

        if (tuple.getColumn(CNAME_TAGS) != null) {
            tags.addAll(tuple.getColumn(CNAME_TAGS));
        }
    }

    public UUID getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(TimelineBandType type) {
        this.type = type;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        this.tags.addAll(tags);
    }

    public void setProperties(Map<String, String> properties) {
        this.properties.clear();
        this.properties.putAll(properties);
    }

    public org.yamcs.protobuf.TimelineBand toProtobuf() {
        org.yamcs.protobuf.TimelineBand.Builder b = org.yamcs.protobuf.TimelineBand.newBuilder()
                .setId(id.toString())
                .setType(type)
                .setShared(shared)
                .setUsername(username)
                .putAllProperties(properties)
                .addAllTags(tags);
        if (name != null) {
            b.setName(name);
        }
        if (description != null) {
            b.setDescription(description);
        }
        return b.build();
    }

    public Tuple toTuple() {
        Tuple tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addColumn(CNAME_TYPE, type.toString());
        tuple.addColumn(CNAME_NAME, name);
        tuple.addColumn(CNAME_DESCRIPTION, description);
        tuple.addColumn(CNAME_SHARED, shared);
        tuple.addColumn(CNAME_USERNAME, username);
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            tuple.addColumn(PROP_PREFIX + entry.getKey(), entry.getValue());
        }
        if (!tags.isEmpty()) {
            tuple.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM), tags);
        }

        return tuple;
    }
}
