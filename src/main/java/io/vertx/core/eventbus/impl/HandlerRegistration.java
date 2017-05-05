package io.vertx.core.eventbus.impl;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.impl.Arguments;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.streams.ReadStream;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/*
 * This class is optimised for performance when used on the same event loop it was created on.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 */
// TODO: 16/12/22 by zmyer
public class HandlerRegistration<T> implements MessageConsumer<T>, Handler<Message<T>> {

    private static final Logger log = LoggerFactory.getLogger(HandlerRegistration.class);
    //默认最大缓存消息数目
    public static final int DEFAULT_MAX_BUFFERED_MESSAGES = 1000;

    //vertx节点
    private final Vertx vertx;
    //事件总线统计对象
    private final EventBusMetrics metrics;
    //事件总线对象
    private final EventBusImpl eventBus;
    //消费者地址信息
    private final String address;
    //消息应答地址
    private final String repliedAddress;
    //
    private final boolean localOnly;
    //异步结果处理对象
    private final Handler<AsyncResult<Message<T>>> asyncResultHandler;
    private long timeoutID = -1;
    private boolean registered;
    //消息处理对象
    private Handler<Message<T>> handler;
    //处理上下文对象
    private Context handlerContext;
    //异步结果对象
    private AsyncResult<Void> result;
    //完成处理对象
    private Handler<AsyncResult<Void>> completionHandler;
    //结束处理对象
    private Handler<Void> endHandler;
    //消息丢弃处理对象
    private Handler<Message<T>> discardHandler;
    //最大缓存消息对象
    private int maxBufferedMessages = DEFAULT_MAX_BUFFERED_MESSAGES;
    //消息挂起队列
    private final Queue<Message<T>> pending = new ArrayDeque<>(8);
    private boolean paused;
    private Object metric;

    // TODO: 16/12/23 by zmyer
    public HandlerRegistration(Vertx vertx, EventBusMetrics metrics, EventBusImpl eventBus, String address,
                               String repliedAddress, boolean localOnly,
                               Handler<AsyncResult<Message<T>>> asyncResultHandler, long timeout) {
        this.vertx = vertx;
        this.metrics = metrics;
        this.eventBus = eventBus;
        this.address = address;
        this.repliedAddress = repliedAddress;
        this.localOnly = localOnly;
        this.asyncResultHandler = asyncResultHandler;
        if (timeout != -1) {
            timeoutID = vertx.setTimer(timeout, tid -> {
                metrics.replyFailure(address, ReplyFailure.TIMEOUT);
                sendAsyncResultFailure(ReplyFailure.TIMEOUT, "Timed out after waiting " + timeout + "(ms) for a reply. address: " + address);
            });
        }
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized MessageConsumer<T> setMaxBufferedMessages(int maxBufferedMessages) {
        Arguments.require(maxBufferedMessages >= 0, "Max buffered messages cannot be negative");
        while (pending.size() > maxBufferedMessages) {
            pending.poll();
        }
        this.maxBufferedMessages = maxBufferedMessages;
        return this;
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized int getMaxBufferedMessages() {
        return maxBufferedMessages;
    }

    @Override
    public String address() {
        return address;
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized void completionHandler(Handler<AsyncResult<Void>> completionHandler) {
        Objects.requireNonNull(completionHandler);
        if (result != null) {
            AsyncResult<Void> value = result;
            vertx.runOnContext(v -> completionHandler.handle(value));
        } else {
            this.completionHandler = completionHandler;
        }
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized void unregister() {
        unregister(false);
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized void unregister(Handler<AsyncResult<Void>> completionHandler) {
        Objects.requireNonNull(completionHandler);
        doUnregister(completionHandler, false);
    }

    // TODO: 16/12/23 by zmyer
    public void unregister(boolean callEndHandler) {
        doUnregister(null, callEndHandler);
    }

    // TODO: 16/12/23 by zmyer
    public void sendAsyncResultFailure(ReplyFailure failure, String msg) {
        unregister();
        asyncResultHandler.handle(Future.failedFuture(new ReplyException(failure, msg)));
    }

    // TODO: 16/12/23 by zmyer
    private void doUnregister(Handler<AsyncResult<Void>> completionHandler, boolean callEndHandler) {
        if (timeoutID != -1) {
            //取消定时器
            vertx.cancelTimer(timeoutID);
        }
        if (endHandler != null && callEndHandler) {
            Handler<Void> theEndHandler = endHandler;
            Handler<AsyncResult<Void>> handler = completionHandler;
            completionHandler = ar -> {
                //执行结束流程
                theEndHandler.handle(null);
                if (handler != null) {
                    //执行结果处理流程
                    handler.handle(ar);
                }
            };
        }
        if (registered) {
            //如果已经注册,则标记未注册
            registered = false;
            //从事件总线上删除该消费者对象
            eventBus.removeRegistration(address, this, completionHandler);
        } else {
            //如果未注册,则调用异步完成接口
            callCompletionHandlerAsync(completionHandler);
        }
    }

    // TODO: 16/12/23 by zmyer
    private void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
        if (completionHandler != null) {
            vertx.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
        }
    }

    // TODO: 16/12/23 by zmyer
    synchronized void setHandlerContext(Context context) {
        handlerContext = context;
    }

    // TODO: 16/12/23 by zmyer
    public synchronized void setResult(AsyncResult<Void> result) {
        this.result = result;
        if (completionHandler != null) {
            if (result.succeeded()) {
                //如果结果处理完毕,则继续后续的注册流程
                metric = metrics.handlerRegistered(address, repliedAddress);
            }
            Handler<AsyncResult<Void>> callback = completionHandler;
            //异步执行注册完毕流程
            vertx.runOnContext(v -> callback.handle(result));
        } else if (result.failed()) {
            log.error("Failed to propagate registration for handler " + handler + " and address " + address);
        } else {
            //同步模式处理注册完毕流程
            metric = metrics.handlerRegistered(address, repliedAddress);
        }
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public void handle(Message<T> message) {
        Handler<Message<T>> theHandler;
        synchronized (this) {
            if (paused) {
                if (pending.size() < maxBufferedMessages) {
                    //先将消息出入到挂起队列中
                    pending.add(message);
                } else {
                    if (discardHandler != null) {
                        //如果挂起队列中的消息数目超过了限制,则需要丢弃后续的消息
                        discardHandler.handle(message);
                    } else {
                        log.warn("Discarding message as more than " + maxBufferedMessages + " buffered in paused consumer");
                    }
                }
                return;
            } else {
                if (pending.size() > 0) {
                    //继续插入消息
                    pending.add(message);
                    //从挂起队列中读取消息
                    message = pending.poll();
                }
                theHandler = handler;
            }
        }
        //开始分发消息
        deliver(theHandler, message);
    }

    // TODO: 16/12/23 by zmyer
    private void deliver(Handler<Message<T>> theHandler, Message<T> message) {
        // Handle the message outside the sync block
        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=473714
        checkNextTick();
        boolean local = true;
        //如果是集群消息
        if (message instanceof ClusteredMessage) {
            // A bit hacky
            ClusteredMessage cmsg = (ClusteredMessage) message;
            if (cmsg.isFromWire()) {
                local = false;
            }
        }
        //从消息头部读取认证地址
        String creditsAddress = message.headers().get(MessageProducerImpl.CREDIT_ADDRESS_HEADER_NAME);
        if (creditsAddress != null) {
            //如果认证地址不为空,则需要向该地址发送认证消息
            eventBus.send(creditsAddress, 1);
        }
        try {
            metrics.beginHandleMessage(metric, local);
            //开始处理消息
            theHandler.handle(message);
            metrics.endHandleMessage(metric, null);
        } catch (Exception e) {
            log.error("Failed to handleMessage", e);
            metrics.endHandleMessage(metric, e);
            throw e;
        }
    }

    // TODO: 16/12/23 by zmyer
    private synchronized void checkNextTick() {
        // Check if there are more pending messages in the queue that can be processed next time around
        if (!pending.isEmpty()) {
            handlerContext.runOnContext(v -> {
                Message<T> message;
                Handler<Message<T>> theHandler;
                synchronized (HandlerRegistration.this) {
                    if (paused || (message = pending.poll()) == null) {
                        return;
                    }
                    theHandler = handler;
                }
                //开始分发消息
                deliver(theHandler, message);
            });
        }
    }

    /*
     * Internal API for testing purposes.
     */
    public synchronized void discardHandler(Handler<Message<T>> handler) {
        this.discardHandler = handler;
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized MessageConsumer<T> handler(Handler<Message<T>> handler) {
        this.handler = handler;
        if (this.handler != null && !registered) {
            //标记注册
            registered = true;
            //将该消息消费者注册到事件总线上
            eventBus.addRegistration(address, this, repliedAddress != null, localOnly);
        } else if (this.handler == null && registered) {
            // This will set registered to false
            //否则需要注销
            this.unregister();
        }
        return this;
    }

    @Override
    public ReadStream<T> bodyStream() {
        return new BodyReadStream<>(this);
    }

    @Override
    public synchronized boolean isRegistered() {
        return registered;
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized MessageConsumer<T> pause() {
        if (!paused) {
            paused = true;
        }
        return this;
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized MessageConsumer<T> resume() {
        if (paused) {
            paused = false;
            checkNextTick();
        }
        return this;
    }

    // TODO: 16/12/23 by zmyer
    @Override
    public synchronized MessageConsumer<T> endHandler(Handler<Void> endHandler) {
        if (endHandler != null) {
            // We should use the HandlerHolder context to properly do this (needs small refactoring)
            Context endCtx = vertx.getOrCreateContext();
            this.endHandler = v1 -> endCtx.runOnContext(v2 -> endHandler.handle(null));
        } else {
            this.endHandler = null;
        }
        return this;
    }

    @Override
    public synchronized MessageConsumer<T> exceptionHandler(Handler<Throwable> handler) {
        return this;
    }

    public Handler<Message<T>> getHandler() {
        return handler;
    }

    public Object getMetric() {
        return metric;
    }

}
