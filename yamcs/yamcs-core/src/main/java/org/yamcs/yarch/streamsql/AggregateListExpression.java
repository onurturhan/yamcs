package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.CompiledAggregateExpression;
import org.yamcs.yarch.CompiledAggregateList;
import org.yamcs.yarch.DataType;

import org.yamcs.utils.parser.ParseException;

public class AggregateListExpression extends AggregateExpression {
    public AggregateListExpression(Expression[] args, boolean star) throws ParseException {
        super(args, star);
    }

    @Override
    protected void doBind() throws StreamSqlException {
        if (star) {
            type = DataType.array(DataType.tuple(inputDef));
        } else {
            // TODO
        }
    }

    @Override
    public CompiledAggregateExpression getCompiledAggregate() throws StreamSqlException {
        return new CompiledAggregateList();
    }
}
