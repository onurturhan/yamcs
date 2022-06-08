package org.yamcs.yarch.streamsql;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * common implementation for statements which do not return a stream of results but just a limited set
 *
 */
public abstract class SimpleStreamSqlStatement implements StreamSqlStatement {
    final static TupleDefinition EMPTY_TDEF = new TupleDefinition();

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener, long limit) throws StreamSqlException {
        resultListener.start(getResultDefinition());

        AtomicLong count = new AtomicLong();
        execute(c, t -> {
            if (count.getAndIncrement() < limit) {
                resultListener.next(t);
            }
        });
        resultListener.complete();
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        StreamSqlResultList r = new StreamSqlResultList();
        execute(c, t -> r.addTuple(t));
        c.close();
        return r.init();
    }

    protected abstract void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException;

    protected TupleDefinition getResultDefinition() {
        return EMPTY_TDEF;
    }
}
