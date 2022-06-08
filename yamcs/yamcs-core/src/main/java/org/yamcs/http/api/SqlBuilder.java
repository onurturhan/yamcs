package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Timestamp;

public class SqlBuilder {

    private String table;
    private List<String> selectExpressions = new ArrayList<>();
    private List<String> conditions = new ArrayList<>(2);
    private Boolean descend;
    private Long offset;
    private Long limit;

    private List<Object> queryArgs = new ArrayList<>();

    public SqlBuilder(String table) {
        this.table = table;
    }

    /**
     * Additive! Calling multiple times will add extra select expressions to the already specified list.
     */
    public SqlBuilder select(String... exprs) {
        for (String expr : exprs) {
            selectExpressions.add(expr);
        }
        return this;
    }

    /**
     * Additive! Calling multiple times will add extra conditions to the already specified list.
     */
    public SqlBuilder where(String whereCondition, Object... args) {
        conditions.add(whereCondition);
        for (Object o : args) {
            queryArgs.add(o);
        }
        return this;
    }

    public SqlBuilder whereColAfter(String colName, Timestamp timestamp) {
        return whereColAfter(colName, timestamp, true);
    }

    public SqlBuilder whereColAfterOrEqual(String colName, Timestamp timestamp) {
        return whereColAfter(colName, timestamp, false);
    }

    private SqlBuilder whereColAfter(String colName, Timestamp timestamp, boolean strict) {
        StringBuilder cond = new StringBuilder();
        cond.append(colName);
        cond.append(strict ? " > " : " >= ");
        cond.append(TimeEncoding.fromProtobufTimestamp(timestamp));
        conditions.add(cond.toString());
        return this;
    }

    public SqlBuilder whereColBeforeOrEqual(String colName, Timestamp timestamp) {
        return whereColBefore(colName, timestamp, false);
    }

    public SqlBuilder whereColBefore(String colName, Timestamp timestamp) {
        return whereColBefore(colName, timestamp, true);
    }

    private SqlBuilder whereColBefore(String colName, Timestamp timestamp, boolean strict) {
        StringBuilder cond = new StringBuilder();
        cond.append(colName);
        cond.append(strict ? " < " : " <= ");
        cond.append(TimeEncoding.fromProtobufTimestamp(timestamp));
        conditions.add(cond.toString());
        return this;
    }

    public SqlBuilder whereColIn(String colName, Collection<?> values) {
        StringBuilder cond = new StringBuilder();
        cond.append(colName).append(" IN (");
        boolean first = true;
        for (Object o : values) {
            if (first) {
                first = false;
            } else {
                cond.append(", ");
            }
            cond.append("?");
            queryArgs.add(o);
        }
        cond.append(")");
        conditions.add(cond.toString());

        return this;
    }

    public SqlBuilder descend(boolean descend) {
        this.descend = descend;
        return this;
    }

    public SqlBuilder limit(long limit) {
        this.limit = limit;
        return this;
    }

    public SqlBuilder limit(long offset, long limit) {
        this.offset = offset;
        this.limit = limit;
        return this;
    }

    public List<Object> getQueryArguments() {
        return queryArgs;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("select ");
        if (selectExpressions.isEmpty()) {
            buf.append("*");
        } else {
            boolean first = true;
            for (String expr : selectExpressions) {
                if (!first) {
                    buf.append(", ");
                }
                buf.append(expr);
                first = false;
            }
        }
        buf.append(" from ").append(table);
        if (!conditions.isEmpty()) {
            buf.append(" where ");
            boolean first = true;
            for (String condition : conditions) {
                if (!first) {
                    buf.append(" and ");
                } else {
                    first = false;
                }
                buf.append(condition);
            }
        }
        if (descend != null) {
            buf.append(descend ? " order desc" : " order asc");
        }
        if (limit != null) {
            if (offset != null) {
                buf.append(" limit ").append(offset).append(",").append(limit);
            } else {
                buf.append(" limit ").append(limit);
            }
        }
        return buf.toString();
    }
}
