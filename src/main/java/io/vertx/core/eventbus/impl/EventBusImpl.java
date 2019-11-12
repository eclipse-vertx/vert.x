/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A local event bus implementation
 *
 * @author <a href="http://tfox.org">Tim Fox</a>                                                                                        T
 */
public class EventBusImpl implements EventBus, MetricsProvider {

  static final Logger log = LoggerFactory.getLogger(EventBusImpl.class);

  private final List<Handler<DeliveryContext>> sendInterceptors = new CopyOnWriteArrayList<>();
  private final List<Handler<DeliveryContext>> receiveInterceptors = new CopyOnWriteArrayList<>();
  private final AtomicLong replySequence = new AtomicLong(0);
  protected final VertxInternal vertx;
  protected final EventBusMetrics metrics;
  protected final ConcurrentMap<String, ConcurrentCyclicSequence<HandlerHolder>> handlerMap = new ConcurrentHashMap<>();
  protected final CodecManager codecManager = new CodecManager();
  protected volatile boolean started;
  private final ContextInternal sendNoContext;

  public EventBusImpl(VertxInternal vertx) {
    VertxMetrics metrics = vertx.metricsSPI();
    this.vertx = vertx;
    this.metrics = metrics != null ? metrics.createEventBusMetrics() : null;
    this.sendNoContext = vertx.getOrCreateContext();
  }

  @Override
  public <T> EventBus addOutboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    sendInterceptors.add((Handler) interceptor);
    return this;
  }

  @Override
  public <T> EventBus addInboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    receiveInterceptors.add((Handler)interceptor);
    return this;
  }

  @Override
  public <T> EventBus removeOutboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    sendInterceptors.remove(interceptor);
    return this;
  }

  Iterator<Handler<DeliveryContext>> receiveInterceptors() {
    return receiveInterceptors.iterator();
  }

  @Override
  public <T> EventBus removeInboundInterceptor(Handler<DeliveryContext<T>> interceptor) {
    receiveInterceptors.remove(interceptor);
    return this;
  }

  public synchronized void start(Handler<AsyncResult<Void>> completionHandler) {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    started = true;
    completionHandler.handle(Future.succeededFuture());
  }

  @Override
  public EventBus send(String address, Object message) {
    return send(address, message, new DeliveryOptions());
  }

  @Override
  public EventBus send(String address, Object message, DeliveryOptions options) {
    MessageImpl msg = createMessage(true, address, options.getHeaders(), message, options.getCodecName());
    sendOrPubInternal(msg, options, null, null);
    return this;
  }

  @Override
  public <T> Future<Message<T>> request(String address, Object message, DeliveryOptions options) {
    MessageImpl msg = createMessage(true, address, options.getHeaders(), message, options.getCodecName());
    ReplyHandler<T> handler = createReplyHandler(msg, true, options);
    sendOrPubInternal(msg, options, handler, null);
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
    sendOrPubInternal(createMessage(false, address, options.getHeaders(), message, options.getCodecName()), options, null, null);
    return this;
  }

  @Override
  public <T> MessageConsumer<T> consumer(String address) {
    checkStarted();
    Objects.requireNonNull(address, "address");
    return new MessageConsumerImpl<>(vertx, vertx.getOrCreateContext(), this, address,  false);
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
    return new MessageConsumerImpl<>(vertx, vertx.getOrCreateContext(), this, address,  true);
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
    return metrics != null;
  }

  @Override
  public EventBusMetrics<?> getMetrics() {
    return metrics;
  }

  public MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec = codecManager.lookupCodec(body, codecName);
    @SuppressWarnings("unchecked")
    MessageImpl msg = new MessageImpl(address, headers, body, codec, send, this);
    return msg;
  }

  protected <T> HandlerHolder<T> addRegistration(String address,
                                                 HandlerRegistration<T> registration,
                                                 boolean replyHandler,
                                                 boolean localOnly,
                                                 Handler<AsyncResult<Void>> completionHandler) {
//    Objects.requireNonNull(registration.getHandler(), "handler");
    LocalRegistrationResult<T> result = addLocalRegistration(address, registration, replyHandler, localOnly);
    addRegistration(result.newAddress, result.holder, completionHandler);
    return result.holder;
  }

  protected <T> void addRegistration(boolean newAddress, HandlerHolder<T> holder, Handler<AsyncResult<Void>> completionHandler) {
    completionHandler.handle(Future.succeededFuture());
  }

  private static class LocalRegistrationResult<T> {
    final HandlerHolder<T> holder;
    final boolean newAddress;
    LocalRegistrationResult(HandlerHolder<T> holder, boolean newAddress) {
      this.holder = holder;
      this.newAddress = newAddress;
    }
  }

  private <T> LocalRegistrationResult<T> addLocalRegistration(String address, HandlerRegistration<T> registration,
                                                           boolean replyHandler, boolean localOnly) {
    Objects.requireNonNull(address, "address");

    Context context = registration.context;

    HandlerHolder<T> holder = new HandlerHolder<>(registration, address, replyHandler, localOnly, context);

    ConcurrentCyclicSequence<HandlerHolder> handlers = new ConcurrentCyclicSequence<HandlerHolder>().add(holder);
    ConcurrentCyclicSequence<HandlerHolder> actualHandlers = handlerMap.merge(
      address,
      handlers,
      (old, prev) -> old.add(prev.first()));

    if (context.deploymentID() != null) {
      HandlerEntry entry = new HandlerEntry<>(address, registration);
      context.addCloseHook(entry);
    }

    boolean newAddress = handlers == actualHandlers;
    return new LocalRegistrationResult<>(holder, newAddress);
  }

  protected <T> void removeRegistration(HandlerHolder<T> holder, Promise<Void> promise) {
    boolean last = removeLocalRegistration(holder);
    removeRegistration(last ? holder : null, holder.address, promise);
  }

  protected <T> void removeRegistration(HandlerHolder<T> handlerHolder, String address,
                                        Promise<Void> promise) {
    promise.complete();
  }

  private <T> boolean removeLocalRegistration(HandlerHolder<T> holder) {
    boolean last = handlerMap.compute(holder.address, (key, val) -> {
      if (val == null) {
        return null;
      }
      ConcurrentCyclicSequence<HandlerHolder> next = val.remove(holder);
      return next.size() == 0 ? null : next;
    }) == null;
    if (holder.setRemoved() && holder.getContext().deploymentID() != null) {
      holder.getContext().removeCloseHook(new HandlerEntry<>(holder.address, holder.getHandler()));
    }
    return last;
  }

  protected <T> void sendReply(MessageImpl replyMessage, DeliveryOptions options, ReplyHandler<T> replyHandler) {
    if (replyMessage.address() == null) {
      throw new IllegalStateException("address not specified");
    } else {
      ContextInternal ctx = vertx.getOrCreateContext();
      if (ctx == null) {
        // Guarantees the order when there is no current context in clustered mode
        ctx = sendNoContext;
      }
      sendOrPubInternal(new OutboundDeliveryContext<>(ctx, replyMessage, options, replyHandler, null));
    }
  }

  protected <T> void sendOrPub(OutboundDeliveryContext<T> sendContext) {
    sendLocally(sendContext);
  }

  protected void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
    if (completionHandler != null) {
      vertx.runOnContext(v -> {
        completionHandler.handle(Future.succeededFuture());
      });
    }
  }

  private <T> void sendLocally(OutboundDeliveryContext<T> sendContext) {
    ReplyException failure = deliverMessageLocally(sendContext.message);
    if (failure != null) {
      sendContext.written(failure);
    } else {
      sendContext.written(null);
    }
  }

  protected boolean isMessageLocal(MessageImpl msg) {
    return true;
  }

  protected ReplyException deliverMessageLocally(MessageImpl msg) {
    ConcurrentCyclicSequence<HandlerHolder> handlers = handlerMap.get(msg.address());
    if (handlers != null) {
      if (msg.isSend()) {
        //Choose one
        HandlerHolder holder = handlers.next();
        if (metrics != null) {
          metrics.messageReceived(msg.address(), !msg.isSend(), isMessageLocal(msg), holder != null ? 1 : 0);
        }
        if (holder != null) {
          deliverToHandler(msg, holder);
        } else {
          // RACY issue !!!!!
        }
      } else {
        // Publish
        if (metrics != null) {
          metrics.messageReceived(msg.address(), !msg.isSend(), isMessageLocal(msg), handlers.size());
        }
        for (HandlerHolder holder: handlers) {
          deliverToHandler(msg, holder);
        }
      }
      return null;
    } else {
      if (metrics != null) {
        metrics.messageReceived(msg.address(), !msg.isSend(), isMessageLocal(msg), 0);
      }
      return new ReplyException(ReplyFailure.NO_HANDLERS, "No handlers for address " + msg.address);
    }
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
                                               ReplyHandler<T> handler, Promise<Void> writePromise) {
    ContextInternal ctx = vertx.getContext();
    if (ctx == null) {
      // Guarantees the order when there is no current context in clustered mode
      ctx = sendNoContext;
    }
    return new OutboundDeliveryContext<>(ctx, message, options, handler, writePromise);
  }

  public <T> void sendOrPubInternal(OutboundDeliveryContext<T> senderCtx) {
    checkStarted();
    senderCtx.iter = sendInterceptors.iterator();
    senderCtx.bus = this;
    senderCtx.metrics = metrics;
    senderCtx.next();
  }

  public <T> void sendOrPubInternal(MessageImpl message, DeliveryOptions options,
                                    ReplyHandler<T> handler, Promise<Void> writePromise) {
    checkStarted();
    sendOrPubInternal(newSendContext(message, options, handler, writePromise));
  }

  private void unregisterAll() {
    // Unregister all handlers explicitly - don't rely on context hooks
    for (ConcurrentCyclicSequence<HandlerHolder> handlers: handlerMap.values()) {
      for (HandlerHolder holder: handlers) {
        holder.getHandler().unregister(ar -> {});
      }
    }
  }

  private <T> void deliverToHandler(MessageImpl msg, HandlerHolder<T> holder) {
    // Each handler gets a fresh copy
    MessageImpl copied = msg.copyBeforeReceive();

    holder.getContext().runOnContext((v) -> {
      // Need to check handler is still there - the handler might have been removed after the message were sent but
      // before it was received
      try {
        if (!holder.isRemoved()) {
          holder.getHandler().receive(copied);
        }
      } finally {
        if (holder.isReplyHandler()) {
          holder.getHandler().unregister(ar -> {});
        }
      }
    });
  }

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
    public void close(Promise<Void> completion) {
      handler.unregister(completion);
    }

  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close(ar -> {});
    super.finalize();
  }

}

