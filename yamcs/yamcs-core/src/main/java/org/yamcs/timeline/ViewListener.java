package org.yamcs.timeline;

public interface ViewListener {

    void next(TimelineView view);

    /**
     * 
     * If a paged request has been performed, the token can be used to retrieve the next chunk.
     * <p>
     * token is null if there was no limit or there were less items than the specified limit
     */
    void complete(String token);

    void completeExceptionally(Throwable t);
}
