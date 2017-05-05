package io.vertx.core.eventbus.impl;

import io.vertx.core.Context;
import io.vertx.core.spi.metrics.EventBusMetrics;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/19 by zmyer
public class HandlerHolder<T> {
    //事件总线统计对象
    private final EventBusMetrics metrics;
    //事件执行上下文对象
    private final Context context;
    //
    private final HandlerRegistration<T> handler;
    private final boolean replyHandler;
    private final boolean localOnly;
    private boolean removed;

    // TODO: 16/12/25 by zmyer
    public HandlerHolder(EventBusMetrics metrics, HandlerRegistration<T> handler, boolean replyHandler, boolean localOnly,
                         Context context) {
        this.metrics = metrics;
        this.context = context;
        this.handler = handler;
        this.replyHandler = replyHandler;
        this.localOnly = localOnly;
    }

    // We use a synchronized block to protect removed as it can be unregistered from a different thread
    public void setRemoved() {
        boolean unregisterMetric = false;
        synchronized (this) {
            if (!removed) {
                removed = true;
                unregisterMetric = true;
            }
        }
        if (unregisterMetric) {
            metrics.handlerUnregistered(handler.getMetric());
        }
    }

    // Because of biased locks the overhead of the synchronized lock should be very low as it's almost always
    // called by the same event loop
    public synchronized boolean isRemoved() {
        return removed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandlerHolder that = (HandlerHolder) o;
        return handler != null ? handler.equals(that.handler) : that.handler == null;
    }

    @Override
    public int hashCode() {
        return handler != null ? handler.hashCode() : 0;
    }

    public Context getContext() {
        return context;
    }

    public HandlerRegistration<T> getHandler() {
        return handler;
    }

    public boolean isReplyHandler() {
        return replyHandler;
    }

    public boolean isLocalOnly() {
        return localOnly;
    }
}
