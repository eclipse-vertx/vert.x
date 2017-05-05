/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.*;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A local event bus implementation
 *
 * @author <a href="http://tfox.org">Tim Fox</a>                                                                                        T
 */
// TODO: 16/12/18 by zmyer
public class EventBusImpl implements EventBus, MetricsProvider {

    private static final Logger log = LoggerFactory.getLogger(EventBusImpl.class);

    //拦截器集合
    private final List<Handler<SendContext>> interceptors = new CopyOnWriteArrayList<>();
    //应答序号器
    private final AtomicLong replySequence = new AtomicLong(0);
    //vertx节点对象
    protected final VertxInternal vertx;
    //事件总线统计对象
    protected final EventBusMetrics metrics;
    //事件处理器集合
    protected final ConcurrentMap<String, Handlers> handlerMap = new ConcurrentHashMap<>();
    //编码器集合
    protected final CodecManager codecManager = new CodecManager();
    //开始标记
    protected volatile boolean started;

    // TODO: 16/12/18 by zmyer
    public EventBusImpl(VertxInternal vertx) {
        this.vertx = vertx;
        this.metrics = vertx.metricsSPI().createMetrics(this);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus addInterceptor(Handler<SendContext> interceptor) {
        //添加拦截器对象
        interceptors.add(interceptor);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus removeInterceptor(Handler<SendContext> interceptor) {
        interceptors.remove(interceptor);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    public synchronized void start(Handler<AsyncResult<Void>> completionHandler) {
        if (started) {
            throw new IllegalStateException("Already started");
        }
        //启动
        started = true;
        //开始处理完成启动事件
        completionHandler.handle(Future.succeededFuture());
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus send(String address, Object message) {
        return send(address, message, new DeliveryOptions(), null);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> EventBus send(String address, Object message, Handler<AsyncResult<Message<T>>> replyHandler) {
        return send(address, message, new DeliveryOptions(), replyHandler);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus send(String address, Object message, DeliveryOptions options) {
        return send(address, message, options, null);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> EventBus send(String address, Object message, DeliveryOptions options, Handler<AsyncResult<Message<T>>> replyHandler) {
        sendOrPubInternal(createMessage(true, address, options.getHeaders(), message, options.getCodecName()), options, replyHandler);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageProducer<T> sender(String address) {
        Objects.requireNonNull(address, "address");
        //创建消息发送者对象
        return new MessageProducerImpl<>(vertx, address, true, new DeliveryOptions());
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageProducer<T> sender(String address, DeliveryOptions options) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(options, "options");
        return new MessageProducerImpl<>(vertx, address, true, options);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageProducer<T> publisher(String address) {
        Objects.requireNonNull(address, "address");
        return new MessageProducerImpl<>(vertx, address, false, new DeliveryOptions());
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageProducer<T> publisher(String address, DeliveryOptions options) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(options, "options");
        return new MessageProducerImpl<>(vertx, address, false, options);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus publish(String address, Object message) {
        return publish(address, message, new DeliveryOptions());
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus publish(String address, Object message, DeliveryOptions options) {
        sendOrPubInternal(createMessage(false, address, options.getHeaders(), message, options.getCodecName()), options, null);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageConsumer<T> consumer(String address) {
        checkStarted();
        Objects.requireNonNull(address, "address");
        //创建消息消费者对象
        return new HandlerRegistration<>(vertx, metrics, this, address, null, false, null, -1);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageConsumer<T> consumer(String address, Handler<Message<T>> handler) {
        Objects.requireNonNull(handler, "handler");
        //创建消息消费者对象
        MessageConsumer<T> consumer = consumer(address);
        //设置消息消费者处理消息流程
        consumer.handler(handler);
        //返回消息消费者
        return consumer;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageConsumer<T> localConsumer(String address) {
        checkStarted();
        Objects.requireNonNull(address, "address");
        return new HandlerRegistration<>(vertx, metrics, this, address, null, true, null, -1);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> MessageConsumer<T> localConsumer(String address, Handler<Message<T>> handler) {
        Objects.requireNonNull(handler, "handler");
        MessageConsumer<T> consumer = localConsumer(address);
        consumer.handler(handler);
        return consumer;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus registerCodec(MessageCodec codec) {
        //注册编码器对象
        codecManager.registerCodec(codec);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus unregisterCodec(String name) {
        codecManager.unregisterCodec(name);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <T> EventBus registerDefaultCodec(Class<T> clazz, MessageCodec<T, ?> codec) {
        codecManager.registerDefaultCodec(clazz, codec);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public EventBus unregisterDefaultCodec(Class clazz) {
        codecManager.unregisterDefaultCodec(clazz);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        checkStarted();
        unregisterAll();
        if (metrics != null) {
            metrics.close();
        }
        if (completionHandler != null) {
            vertx.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
        }
    }

    @Override
    public boolean isMetricsEnabled() {
        return metrics != null && metrics.isEnabled();
    }

    @Override
    public EventBusMetrics<?> getMetrics() {
        return metrics;
    }

    // TODO: 16/12/18 by zmyer
    protected MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
        Objects.requireNonNull(address, "no null address accepted");
        //首先根据提供的编码器名称,获取相应的编码器对象
        MessageCodec codec = codecManager.lookupCodec(body, codecName);
        //创建消息对象
        @SuppressWarnings("unchecked")
        MessageImpl msg = new MessageImpl(address, null, headers, body, codec, send, this);
        //返回消息对象
        return msg;
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void addRegistration(String address, HandlerRegistration<T> registration,
                                       boolean replyHandler, boolean localOnly) {
        Objects.requireNonNull(registration.getHandler(), "handler");
        //获取本地注册地址
        boolean newAddress = addLocalRegistration(address, registration, replyHandler, localOnly);
        //开始注册
        addRegistration(newAddress, address, replyHandler, localOnly, registration::setResult);
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void addRegistration(boolean newAddress, String address,
                                       boolean replyHandler, boolean localOnly,
                                       Handler<AsyncResult<Void>> completionHandler) {
        completionHandler.handle(Future.succeededFuture());
    }

    // TODO: 16/12/18 by zmyer
    protected <T> boolean addLocalRegistration(String address, HandlerRegistration<T> registration,
                                               boolean replyHandler, boolean localOnly) {
        Objects.requireNonNull(address, "address");

        //首先读取执行上下文对象
        Context context = Vertx.currentContext();
        boolean hasContext = context != null;
        if (!hasContext) {
            // Embedded
            context = vertx.getOrCreateContext();
        }
        //为注册对象注册上下文对象
        registration.setHandlerContext(context);

        boolean newAddress = false;

        //待注册的事件处理对象
        HandlerHolder holder = new HandlerHolder<>(metrics, registration, replyHandler, localOnly, context);

        //根据地址信息,获取事件处理集合
        Handlers handlers = handlerMap.get(address);
        if (handlers == null) {
            //如果不存在,则需要重新创建
            handlers = new Handlers();
            //将新创建的事件集合注册到事件映射表中
            Handlers prevHandlers = handlerMap.putIfAbsent(address, handlers);
            if (prevHandlers != null) {
                handlers = prevHandlers;
            }
            newAddress = true;
        }
        //将待注册的事件加入到对应的事件处理句柄集合中
        handlers.list.add(holder);

        if (hasContext) {
            HandlerEntry entry = new HandlerEntry<>(address, registration);
            context.addCloseHook(entry);
        }

        return newAddress;
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void removeRegistration(String address, HandlerRegistration<T> handler, Handler<AsyncResult<Void>> completionHandler) {
        //获取待删除的事件处理句柄对象
        HandlerHolder holder = removeLocalRegistration(address, handler);
        //撤销事件处理句柄对象
        removeRegistration(holder, address, completionHandler);
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void removeRegistration(HandlerHolder handlerHolder, String address,
                                          Handler<AsyncResult<Void>> completionHandler) {
        //异步执行删除事件处理句柄对象
        callCompletionHandlerAsync(completionHandler);
    }

    // TODO: 16/12/18 by zmyer
    protected <T> HandlerHolder removeLocalRegistration(String address, HandlerRegistration<T> handler) {
        //获取待删除的事件处理句柄集合
        Handlers handlers = handlerMap.get(address);
        HandlerHolder lastHolder = null;
        if (handlers != null) {
            synchronized (handlers) {
                //获取事件处理句柄集合中的事件处理对象数目
                int size = handlers.list.size();
                // Requires a list traversal. This is tricky to optimise since we can't use a set since
                // we need fast ordered traversal for the round robin
                for (int i = 0; i < size; i++) {
                    //获取事件处理句柄对象
                    HandlerHolder holder = handlers.list.get(i);
                    if (holder.getHandler() == handler) {
                        //删除待删除的事件处理句柄对象
                        handlers.list.remove(i);
                        //设置删除标记
                        holder.setRemoved();
                        if (handlers.list.isEmpty()) {
                            //如果事件处理句柄集合为空,则也需要删除
                            handlerMap.remove(address);
                            lastHolder = holder;
                        }
                        //开始执行删除hook句柄
                        holder.getContext().removeCloseHook(new HandlerEntry<>(address, holder.getHandler()));
                        break;
                    }
                }
            }
        }
        return lastHolder;
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void sendReply(MessageImpl replyMessage, MessageImpl replierMessage, DeliveryOptions options,
                                 Handler<AsyncResult<Message<T>>> replyHandler) {
        if (replyMessage.address() == null) {
            throw new IllegalStateException("address not specified");
        } else {
            //构造应答事件处理器
            HandlerRegistration<T> replyHandlerRegistration = createReplyHandlerRegistration(replyMessage, options, replyHandler);
            //构造应答事件处理执行上下文对象
            new ReplySendContextImpl<>(replyMessage, options, replyHandlerRegistration, replierMessage).next();
        }
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void sendReply(SendContextImpl<T> sendContext, MessageImpl replierMessage) {
        sendOrPub(sendContext);
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void sendOrPub(SendContextImpl<T> sendContext) {
        MessageImpl message = sendContext.message;
        metrics.messageSent(message.address(), !message.send(), true, false);
        //开始在本地投递该消息
        deliverMessageLocally(sendContext);
    }

    // TODO: 16/12/18 by zmyer
    protected <T> Handler<Message<T>> convertHandler(Handler<AsyncResult<Message<T>>> handler) {
        return reply -> {
            Future<Message<T>> result;
            if (reply.body() instanceof ReplyException) {
                // This is kind of clunky - but hey-ho
                ReplyException exception = (ReplyException) reply.body();
                metrics.replyFailure(reply.address(), exception.failureType());
                result = Future.failedFuture(exception);
            } else {
                result = Future.succeededFuture(reply);
            }
            handler.handle(result);
        };
    }

    // TODO: 16/12/18 by zmyer
    protected void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
        if (completionHandler != null) {
            vertx.runOnContext(v -> {
                //
                completionHandler.handle(Future.succeededFuture());
            });
        }
    }

    // TODO: 16/12/18 by zmyer
    protected <T> void deliverMessageLocally(SendContextImpl<T> sendContext) {
        if (!deliverMessageLocally(sendContext.message)) {
            // no handlers
            metrics.replyFailure(sendContext.message.address, ReplyFailure.NO_HANDLERS);
            if (sendContext.handlerRegistration != null) {
                sendContext.handlerRegistration.sendAsyncResultFailure(ReplyFailure.NO_HANDLERS, "No handlers for address "
                        + sendContext.message.address);
            }
        }
    }

    protected boolean isMessageLocal(MessageImpl msg) {
        return true;
    }

    // TODO: 16/12/18 by zmyer
    protected <T> boolean deliverMessageLocally(MessageImpl msg) {
        //为消息对象设置事件总线对象
        msg.setBus(this);
        //根据地址获取事件处理句柄集合
        Handlers handlers = handlerMap.get(msg.address());
        if (handlers != null) {
            //发送消息
            if (msg.send()) {
                //Choose one
                //从事件处理句柄集合中选出一个事件处理句柄对象
                HandlerHolder holder = handlers.choose();
                metrics.messageReceived(msg.address(), !msg.send(), isMessageLocal(msg), holder != null ? 1 : 0);
                if (holder != null) {
                    //开始将该消息投递到对应的事件句柄对象中
                    deliverToHandler(msg, holder);
                }
            } else {
                // Publish
                metrics.messageReceived(msg.address(), !msg.send(), isMessageLocal(msg), handlers.list.size());
                for (HandlerHolder holder : handlers.list) {
                    //向每个事件句柄对象发送该消息对象
                    deliverToHandler(msg, holder);
                }
            }
            return true;
        } else {
            //更新统计信息
            metrics.messageReceived(msg.address(), !msg.send(), isMessageLocal(msg), 0);
            return false;
        }
    }

    protected void checkStarted() {
        if (!started) {
            throw new IllegalStateException("Event Bus is not started");
        }
    }

    protected String generateReplyAddress() {
        return Long.toString(replySequence.incrementAndGet());
    }

    // TODO: 16/12/18 by zmyer
    private <T> HandlerRegistration<T> createReplyHandlerRegistration(MessageImpl message,
                                                                      DeliveryOptions options,
                                                                      Handler<AsyncResult<Message<T>>> replyHandler) {
        if (replyHandler != null) {
            //发送超时时间
            long timeout = options.getSendTimeout();
            //获取应答地址
            String replyAddress = generateReplyAddress();
            message.setReplyAddress(replyAddress);
            //获取应答事件处理对象
            Handler<Message<T>> simpleReplyHandler = convertHandler(replyHandler);
            HandlerRegistration<T> registration =
                    new HandlerRegistration<>(vertx, metrics, this, replyAddress, message.address, true, replyHandler, timeout);
            registration.handler(simpleReplyHandler);
            return registration;
        } else {
            return null;
        }
    }

    // TODO: 16/12/18 by zmyer
    private <T> void sendOrPubInternal(MessageImpl message, DeliveryOptions options,
                                       Handler<AsyncResult<Message<T>>> replyHandler) {
        checkStarted();
        HandlerRegistration<T> replyHandlerRegistration = createReplyHandlerRegistration(message, options, replyHandler);
        SendContextImpl<T> sendContext = new SendContextImpl<>(message, options, replyHandlerRegistration);
        sendContext.next();
    }

    // TODO: 16/12/18 by zmyer
    protected class SendContextImpl<T> implements SendContext<T> {
        //消息对象
        public final MessageImpl message;
        //消息投递配置
        public final DeliveryOptions options;
        //事件处理句柄注册对象
        public final HandlerRegistration<T> handlerRegistration;
        public final Iterator<Handler<SendContext>> iter;

        // TODO: 16/12/18 by zmyer
        public SendContextImpl(MessageImpl message, DeliveryOptions options, HandlerRegistration<T> handlerRegistration) {
            this.message = message;
            this.options = options;
            this.handlerRegistration = handlerRegistration;
            this.iter = interceptors.iterator();
        }

        @Override
        public Message<T> message() {
            return message;
        }

        // TODO: 16/12/18 by zmyer
        @Override
        public void next() {
            if (iter.hasNext()) {
                Handler<SendContext> handler = iter.next();
                try {
                    handler.handle(this);
                } catch (Throwable t) {
                    log.error("Failure in interceptor", t);
                }
            } else {
                sendOrPub(this);
            }
        }

        @Override
        public boolean send() {
            return message.send();
        }
    }

    // TODO: 16/12/18 by zmyer
    protected class ReplySendContextImpl<T> extends SendContextImpl<T> {
        //消息对象
        private final MessageImpl replierMessage;

        // TODO: 16/12/18 by zmyer
        public ReplySendContextImpl(MessageImpl message, DeliveryOptions options, HandlerRegistration<T> handlerRegistration,
                                    MessageImpl replierMessage) {
            super(message, options, handlerRegistration);
            this.replierMessage = replierMessage;
        }

        // TODO: 16/12/18 by zmyer
        @Override
        public void next() {
            if (iter.hasNext()) {
                Handler<SendContext> handler = iter.next();
                handler.handle(this);
            } else {
                sendReply(this, replierMessage);
            }
        }
    }

    // TODO: 16/12/18 by zmyer
    private void unregisterAll() {
        // Unregister all handlers explicitly - don't rely on context hooks
        for (Handlers handlers : handlerMap.values()) {
            for (HandlerHolder holder : handlers.list) {
                holder.getHandler().unregister(true);
            }
        }
    }

    // TODO: 16/12/18 by zmyer
    private <T> void deliverToHandler(MessageImpl msg, HandlerHolder<T> holder) {
        // Each handler gets a fresh copy
        @SuppressWarnings("unchecked")
        Message<T> copied = msg.copyBeforeReceive();

        if (metrics.isEnabled()) {
            metrics.scheduleMessage(holder.getHandler().getMetric(), msg.isLocal());
        }

        holder.getContext().runOnContext((v) -> {
            // Need to check handler is still there - the handler might have been removed after the message were sent but
            // before it was received
            try {
                if (!holder.isRemoved()) {
                    holder.getHandler().handle(copied);
                }
            } finally {
                if (holder.isReplyHandler()) {
                    holder.getHandler().unregister();
                }
            }
        });
    }

    // TODO: 16/12/18 by zmyer
    public class HandlerEntry<T> implements Closeable {
        final String address;
        final HandlerRegistration<T> handler;

        public HandlerEntry(String address, HandlerRegistration<T> handler) {
            this.address = address;
            this.handler = handler;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (this == o) return true;
            if (getClass() != o.getClass()) return false;
            HandlerEntry entry = (HandlerEntry) o;
            if (!address.equals(entry.address)) return false;
            if (!handler.equals(entry.handler)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = address != null ? address.hashCode() : 0;
            result = 31 * result + (handler != null ? handler.hashCode() : 0);
            return result;
        }

        // Called by context on undeploy
        public void close(Handler<AsyncResult<Void>> completionHandler) {
            handler.unregister(completionHandler);
        }

    }

    @Override
    protected void finalize() throws Throwable {
        // Make sure this gets cleaned up if there are no more references to it
        // so as not to leave connections and resources dangling until the system is shutdown
        // which could make the JVM run out of file handles.
        close(ar -> {
        });
        super.finalize();
    }

}

