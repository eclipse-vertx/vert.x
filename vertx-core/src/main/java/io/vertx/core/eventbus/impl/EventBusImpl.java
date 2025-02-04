/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A local event bus implementation
 *
 * @author <a href="http://tfox.org">Tim Fox</a>                                                                                        T
 */
public class EventBusImpl implements EventBusInternal, MetricsProvider {

  private static final AtomicReferenceFieldUpdater<EventBusImpl, Handler[]> OUTBOUND_INTERCEPTORS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(EventBusImpl.class, Handler[].class, "outboundInterceptors");
  private static final AtomicReferenceFieldUpdater<EventBusImpl, Handler[]> INBOUND_INTERCEPTORS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(EventBusImpl.class, Handler[].class, "inboundInterceptors");

  private volatile Handler<DeliveryContext>[] outboundInterceptors = new Handler[0];
  private volatile Handler<DeliveryContext>[] inboundInterceptors = new Handler[0];
  private final AtomicLong replySequence = new AtomicLong(0);
  protected final VertxInternal vertx;
  protected final EventBusMetrics metrics;
  protected final ConcurrentMap<String, ConcurrentCyclicSequence<HandlerHolder>> handlerMap = new ConcurrentHashMap<>();
  protected final CodecManager codecManager = new CodecManager();
  protected volatile boolean started;

  public EventBusImpl(VertxInternal vertx) {
    VertxMetrics metrics = vertx.metricsSPI();
    this.vertx = vertx;
    this.metrics = metrics != null ? metrics.createEventBusMetrics() : null;
  }

  @Override
  public <T> EventBus addOutboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    addInterceptor(OUTBOUND_INTERCEPTORS_UPDATER, Objects.requireNonNull(interceptor));
    return this;
  }

  @Override
  public <T> EventBus addInboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    addInterceptor(INBOUND_INTERCEPTORS_UPDATER, Objects.requireNonNull(interceptor));
    return this;
  }

  @Override
  public <T> EventBus removeOutboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    removeInterceptor(OUTBOUND_INTERCEPTORS_UPDATER, Objects.requireNonNull(interceptor));
    return this;
  }

  @Override
  public <T> EventBus removeInboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    removeInterceptor(INBOUND_INTERCEPTORS_UPDATER, Objects.requireNonNull(interceptor));
    return this;
  }

  Handler<DeliveryContext>[] inboundInterceptors() {
    return inboundInterceptors;
  }

  Handler<DeliveryContext>[] outboundInterceptors() {
    return outboundInterceptors;
  }

  @Override
  public EventBus clusterSerializableChecker(Function<String, Boolean> classNamePredicate) {
    codecManager.clusterSerializableCheck(classNamePredicate);
    return this;
  }

  @Override
  public EventBus serializableChecker(Function<String, Boolean> classNamePredicate) {
    codecManager.serializableCheck(classNamePredicate);
    return this;
  }

  @Override
  public synchronized void start(Promise<Void> promise) {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    started = true;
    promise.complete();
  }

  @Override
  public EventBus send(String address, Object message) {
    return send(address, message, new DeliveryOptions());
  }

  @Override
  public EventBus send(String address, Object message, DeliveryOptions options) {
    MessageImpl msg = createMessage(true, isLocalOnly(options), address, options.getHeaders(), message, options.getCodecName());
    sendOrPubInternal(msg, options, null);
    return this;
  }

  @Override
  public <T> Future<Message<T>> request(String address, Object message, DeliveryOptions options) {
    MessageImpl msg = createMessage(true, isLocalOnly(options), address, options.getHeaders(), message, options.getCodecName());
    ReplyHandler<T> handler = createReplyHandler(msg, true, options);
    sendOrPubInternal(msg, options, handler);
    return handler.result();
  }

  @Override
  public <T> MessageProducer<T> sender(String address) {
    Objects.requireNonNull(address, "address");
    return new MessageProducerImpl<>(vertx, address, true, new DeliveryOptions());
  }

  @Override
  public <T> MessageProducer<T> sender(String address, DeliveryOptions options) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(options, "options");
    return new MessageProducerImpl<>(vertx, address, true, options);
  }

  @Override
  public <T> MessageProducer<T> publisher(String address) {
    Objects.requireNonNull(address, "address");
    return new MessageProducerImpl<>(vertx, address, false, new DeliveryOptions());
  }

  @Override
  public <T> MessageProducer<T> publisher(String address, DeliveryOptions options) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(options, "options");
    return new MessageProducerImpl<>(vertx, address, false, options);
  }

  @Override
  public EventBus publish(String address, Object message) {
    return publish(address, message, new DeliveryOptions());
  }

  @Override
  public EventBus publish(String address, Object message, DeliveryOptions options) {
    sendOrPubInternal(createMessage(false, isLocalOnly(options), address, options.getHeaders(), message, options.getCodecName()), options, null);
    return this;
  }

  @Override
  public <T> MessageConsumer<T> consumer(MessageConsumerOptions options) {
    checkStarted();
    String address = options.getAddress();
    Arguments.require(options.getAddress() != null, "Consumer address must not be null");
    return new MessageConsumerImpl<>(vertx.getOrCreateContext(), this, address, options.isLocalOnly(), options.getMaxBufferedMessages());
  }

  @Override
  public <T> MessageConsumer<T> consumer(MessageConsumerOptions options, Handler<Message<T>> handler) {
    Objects.requireNonNull(handler, "handler");
    MessageConsumer<T> consumer = consumer(options);
    consumer.handler(handler);
    return consumer;
  }

  @Override
  public <T> MessageConsumer<T> consumer(String address) {
    checkStarted();
    Objects.requireNonNull(address, "address");
    return new MessageConsumerImpl<>(vertx.getOrCreateContext(), this, address, false, MessageConsumerOptions.DEFAULT_MAX_BUFFERED_MESSAGES);
  }

  @Override
  public <T> MessageConsumer<T> consumer(String address, Handler<Message<T>> handler) {
    Objects.requireNonNull(handler, "handler");
    MessageConsumer<T> consumer = consumer(address);
    consumer.handler(handler);
    return consumer;
  }

  @Override
  public <T> MessageConsumer<T> localConsumer(String address) {
    checkStarted();
    Objects.requireNonNull(address, "address");
    return new MessageConsumerImpl<>(vertx.getOrCreateContext(), this, address, true, MessageConsumerOptions.DEFAULT_MAX_BUFFERED_MESSAGES);
  }

  @Override
  public <T> MessageConsumer<T> localConsumer(String address, Handler<Message<T>> handler) {
    Objects.requireNonNull(handler, "handler");
    MessageConsumer<T> consumer = localConsumer(address);
    consumer.handler(handler);
    return consumer;
  }

  @Override
  public EventBus registerCodec(MessageCodec codec) {
    codecManager.registerCodec(codec);
    return this;
  }

  @Override
  public EventBus unregisterCodec(String name) {
    codecManager.unregisterCodec(name);
    return this;
  }

  @Override
  public <T> EventBus registerDefaultCodec(Class<T> clazz, MessageCodec<T, ?> codec) {
    codecManager.registerDefaultCodec(clazz, codec);
    return this;
  }

  @Override
  public EventBus unregisterDefaultCodec(Class clazz) {
    codecManager.unregisterDefaultCodec(clazz);
    return this;
  }

  @Override
  public EventBus codecSelector(Function<Object, String> selector) {
    codecManager.codecSelector(selector);
    return this;
  }

  @Override
  public void close(Promise<Void> promise) {
    if (!started) {
      promise.complete();
      return;
    }
    unregisterAll().onComplete(ar -> {
      if (metrics != null) {
        metrics.close();
      }
      promise.handle(ar);
    });
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public EventBusMetrics<?> getMetrics() {
    return metrics;
  }

  public MessageImpl createMessage(boolean send, boolean localOnly, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec = codecManager.lookupCodec(body, codecName, localOnly);
    @SuppressWarnings("unchecked")
    MessageImpl msg = new MessageImpl(address, headers, body, codec, send, this);
    return msg;
  }

  protected <T> Consumer<Promise<Void>> addRegistration(String address, HandlerRegistration<T> registration, boolean broadcast, boolean localOnly, Promise<Void> promise) {
    HandlerHolder<T> holder = addLocalRegistration(address, registration, localOnly);
    if (broadcast) {
      onLocalRegistration(holder, promise);
    } else {
      if (promise != null) {
        promise.complete();
      }
    }
    return p -> {
      removeRegistration(holder, broadcast, p);
    };
  }

  protected <T> void onLocalRegistration(HandlerHolder<T> handlerHolder, Promise<Void> promise) {
    if (promise != null) {
      promise.complete();
    }
  }

  private <T> HandlerHolder<T> addLocalRegistration(String address, HandlerRegistration<T> registration,
                                                    boolean localOnly) {
    Objects.requireNonNull(address, "address");

    ContextInternal context = registration.context;

    HandlerHolder<T> holder = createHandlerHolder(registration, localOnly, context);

    ConcurrentCyclicSequence<HandlerHolder> handlers = new ConcurrentCyclicSequence<HandlerHolder>().add(holder);
    ConcurrentCyclicSequence<HandlerHolder> actualHandlers = handlerMap.merge(
      address,
      handlers,
      (old, prev) -> old.add(prev.first()));

    if (context.isDeployment()) {
      context.addCloseHook(registration);
    }

    return holder;
  }

  protected <T> HandlerHolder<T> createHandlerHolder(HandlerRegistration<T> registration, boolean localOnly, ContextInternal context) {
    return new HandlerHolder<>(registration, localOnly, context);
  }

  protected <T> void removeRegistration(HandlerHolder<T> handlerHolder, boolean broadcast, Promise<Void> promise) {
    removeLocalRegistration(handlerHolder);
    if (broadcast) {
      onLocalUnregistration(handlerHolder, promise);
    } else {
      promise.complete();
    }
  }

  protected <T> void onLocalUnregistration(HandlerHolder<T> handlerHolder, Promise<Void> promise) {
    promise.complete();
  }

  private <T> void removeLocalRegistration(HandlerHolder<T> holder) {
    String address = holder.getHandler().address;
    handlerMap.compute(address, (key, val) -> {
      if (val == null) {
        return null;
      }
      ConcurrentCyclicSequence<HandlerHolder> next = val.remove(holder);
      return next.size() == 0 ? null : next;
    });
    if (holder.setRemoved() && holder.getContext().deploymentID() != null) {
      holder.getContext().removeCloseHook(holder.getHandler());
    }
  }

  protected <T> void sendReply(MessageImpl replyMessage, DeliveryOptions options, ReplyHandler<T> replyHandler) {
    if (replyMessage.address() == null) {
      throw new IllegalStateException("address not specified");
    } else {
      sendOrPubInternal(new OutboundDeliveryContext<>(vertx.getOrCreateContext(), replyMessage, options, replyHandler));
    }
  }

  protected <T> void sendOrPub(ContextInternal ctx, MessageImpl<?, T> message, DeliveryOptions options, Promise<Void> writePromise) {
    sendLocally(message, writePromise);
  }

  protected <T> void sendOrPub(OutboundDeliveryContext<T> sendContext) {
    sendOrPub(sendContext.ctx, sendContext.message, sendContext.options, sendContext);
  }

  protected <T> void sendLocally(MessageImpl<?, T> message, Promise<Void> writePromise) {
    ReplyException failure = deliverMessageLocally(message);
    if (failure != null) {
      writePromise.tryFail(failure);
    } else {
      writePromise.tryComplete();
    }
  }

  protected boolean isMessageLocal(MessageImpl msg) {
    return true;
  }

  protected ReplyException deliverMessageLocally(MessageImpl msg) {
    ConcurrentCyclicSequence<HandlerHolder> handlers = handlerMap.get(msg.address());
    boolean messageLocal = isMessageLocal(msg);
    if (handlers != null) {
      if (msg.isSend()) {
        //Choose one
        HandlerHolder holder = nextHandler(handlers, messageLocal);
        if (metrics != null) {
          metrics.messageReceived(msg.address(), !msg.isSend(), messageLocal, holder != null ? 1 : 0);
        }
        if (holder != null) {
          holder.handler.receive(msg.copyBeforeReceive());
        } else {
          // RACY issue !!!!!
        }
      } else {
        // Publish
        if (metrics != null) {
          metrics.messageReceived(msg.address(), !msg.isSend(), messageLocal, handlers.size());
        }
        for (HandlerHolder holder : handlers) {
          if (messageLocal || !holder.isLocalOnly()) {
            holder.handler.receive(msg.copyBeforeReceive());
          }
        }
      }
      return null;
    } else {
      if (metrics != null) {
        metrics.messageReceived(msg.address(), !msg.isSend(), messageLocal, 0);
      }
      return new ReplyException(ReplyFailure.NO_HANDLERS, "No handlers for address " + msg.address);
    }
  }

  protected HandlerHolder nextHandler(ConcurrentCyclicSequence<HandlerHolder> handlers, boolean messageLocal) {
    return handlers.next();
  }

  protected void checkStarted() {
    if (!started) {
      throw new IllegalStateException("Event Bus is not started");
    }
  }

  protected String generateReplyAddress() {
    return "__vertx.reply." + Long.toString(replySequence.incrementAndGet());
  }

  <T> ReplyHandler<T> createReplyHandler(MessageImpl message,
                                         boolean src,
                                         DeliveryOptions options) {
    long timeout = options.getSendTimeout();
    String replyAddress = generateReplyAddress();
    message.setReplyAddress(replyAddress);
    ReplyHandler<T> handler = new ReplyHandler<>(this, vertx.getOrCreateContext(), replyAddress, message.address, src, timeout);
    handler.register();
    return handler;
  }

  public <T> OutboundDeliveryContext<T> newSendContext(MessageImpl message, DeliveryOptions options,
                                                       ReplyHandler<T> handler) {
    return new OutboundDeliveryContext<>(vertx.getOrCreateContext(), message, options, handler);
  }

  public <T> void sendOrPubInternal(OutboundDeliveryContext<T> senderCtx) {
    checkStarted();
    senderCtx.bus = this;
    senderCtx.metrics = metrics;
    senderCtx.next();
  }

  public <T> Future<Void> sendOrPubInternal(MessageImpl message, DeliveryOptions options,
                                            ReplyHandler<T> handler) {
    checkStarted();
    OutboundDeliveryContext<T> ctx = newSendContext(message, options, handler);
    sendOrPubInternal(ctx);
    Future<Void> future = ctx.writePromise.future();
    if (message.send) {
      return future;
    }
    return future.recover(throwable -> {
      // For publish, we only care if there are no handlers
      if (throwable instanceof ReplyException) {
        return Future.failedFuture(throwable);
      }
      return Future.succeededFuture();
    });
  }

  private Future<Void> unregisterAll() {
    // Unregister all handlers explicitly - don't rely on context hooks
    List<Future<?>> futures = new ArrayList<>();
    for (ConcurrentCyclicSequence<HandlerHolder> handlers : handlerMap.values()) {
      for (HandlerHolder holder : handlers) {
        futures.add(holder.getHandler().unregister());
      }
    }
    return Future.join(futures).mapEmpty();
  }

  private void addInterceptor(AtomicReferenceFieldUpdater<EventBusImpl, Handler[]> updater, Handler interceptor) {
    while (true) {
      Handler[] interceptors = updater.get(this);
      Handler[] copy = Arrays.copyOf(interceptors, interceptors.length + 1);
      copy[interceptors.length] = interceptor;
      if (updater.compareAndSet(this, interceptors, copy)) {
        break;
      }
    }
  }

  private void removeInterceptor(AtomicReferenceFieldUpdater<EventBusImpl, Handler[]> updater, Handler interceptor) {
    while (true) {
      Handler[] interceptors = updater.get(this);
      int idx = -1;
      for (int i = 0; i < interceptors.length; i++) {
        if (interceptors[i].equals(interceptor)) {
          idx = i;
          break;
        }
      }
      if (idx == -1) {
        return;
      }
      Handler<DeliveryContext>[] copy = new Handler[interceptors.length - 1];
      System.arraycopy(interceptors, 0, copy, 0, idx);
      System.arraycopy(interceptors, idx + 1, copy, idx, copy.length - idx);
      if (updater.compareAndSet(this, interceptors, copy)) {
        break;
      }
    }
  }

  private boolean isLocalOnly(DeliveryOptions options) {
    if (vertx.isClustered()) {
      return options.isLocalOnly();
    }
    return true;
  }
}

