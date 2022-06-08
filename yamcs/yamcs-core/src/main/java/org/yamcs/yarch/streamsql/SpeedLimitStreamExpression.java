package org.yamcs.yarch.streamsql;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.SpeedLimitStream;
import org.yamcs.yarch.SpeedSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class SpeedLimitStreamExpression implements StreamExpression {
    SpeedSpec speedSpec;
    StreamExpression expression;
    static AtomicInteger count = new AtomicInteger();

    static Logger log = LoggerFactory.getLogger(SpeedLimitStreamExpression.class.getName());

    SpeedLimitStreamExpression(StreamExpression expression, SpeedSpec speedSpec) {
        this.expression = expression;
        this.speedSpec = speedSpec;
    }

    @Override
    public void bind(ExecutionContext c) throws StreamSqlException {
        expression.bind(c);
    }

    @Override
    public TupleDefinition getOutputDefinition() {
        return expression.getOutputDefinition();
    }

    @Override
    public Stream execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabaseInstance ydb = c.getDb();
        if (speedSpec == null || speedSpec.getType() == SpeedSpec.Type.AFAP) {
            return expression.execute(c);
        } else {
            Stream s = expression.execute(c);
            SpeedLimitStream sls = new SpeedLimitStream(ydb, "speed_limit_" + count.incrementAndGet(),
                    s.getDefinition(), speedSpec);
            s.addSubscriber(sls);
            sls.setSubscribedStream(s);
            return sls;
        }
    }

    @Override
    public boolean isFinite() {
        return false;
    }
}
