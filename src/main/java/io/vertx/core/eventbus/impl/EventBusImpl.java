/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.eventbus.SendContext;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.VertxMetrics;

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
public class EventBusImpl implements EventBus, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(EventBusImpl.class);

  private final List<Handler<SendContext>> interceptors = new CopyOnWriteArrayList<>();
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
  public EventBus addInterceptor(Handler<SendContext> interceptor) {
    interceptors.add(interceptor);
    return this;
  }

  @Override
  public EventBus removeInterceptor(Handler<SendContext> interceptor) {
    interceptors.remove(interceptor);
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
    return send(address, message, new DeliveryOptions(), null);
  }

  @Override
  public <T> EventBus send(String address, Object message, Handler<AsyncResult<Message<T>>> replyHandler) {
    return send(address, message, new DeliveryOptions(), replyHandler);
  }

  @Override
  public EventBus send(String address, Object message, DeliveryOptions options) {
    return send(address, message, options, null);
  }

  @Override
  public <T> EventBus send(String address, Object message, DeliveryOptions options, Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubInternal(createMessage(true, address, options.getHeaders(), message, options.getCodecName()), options, replyHandler);
    return this;
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
    sendOrPubInternal(createMessage(false, address, options.getHeaders(), message, options.getCodecName()), options, null);
    return this;
  }

  @Override
  public <T> MessageConsumer<T> consumer(String address) {
    checkStarted();
    Objects.requireNonNull(address, "address");
    return new HandlerRegistration<>(vertx, metrics, this, address, null, false, null, -1);
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
    return new HandlerRegistration<>(vertx, metrics, this, address, null, true, null, -1);
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

  protected MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec = codecManager.lookupCodec(body, codecName);
    @SuppressWarnings("unchecked")
    MessageImpl msg = new MessageImpl(address, null, headers, body, codec, send, this);
    return msg;
  }

  protected <T> HandlerHolder<T> addRegistration(String address, HandlerRegistration<T> registration,
                                     boolean replyHandler, boolean localOnly) {
    Objects.requireNonNull(registration.getHandler(), "handler");
    LocalRegistrationResult<T> result = addLocalRegistration(address, registration, replyHandler, localOnly);
    addRegistration(result.newAddress, address, replyHandler, localOnly, registration::setResult);
    return result.holder;
  }

  protected <T> void addRegistration(boolean newAddress, String address,
                                     boolean replyHandler, boolean localOnly,
                                     Handler<AsyncResult<Void>> completionHandler) {
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

    Context context = Vertx.currentContext();
    boolean hasContext = context != null;
    if (!hasContext) {
      // Embedded
      context = vertx.getOrCreateContext();
    }
    registration.setHandlerContext(context);

    HandlerHolder<T> holder = new HandlerHolder<>(metrics, registration, replyHandler, localOnly, context);

    ConcurrentCyclicSequence<HandlerHolder> handlers = new ConcurrentCyclicSequence<HandlerHolder>().add(holder);
    ConcurrentCyclicSequence<HandlerHolder> actualHandlers = handlerMap.merge(
      address,
      handlers,
      (old, prev) -> old.add(prev.first()));

    if (hasContext) {
      HandlerEntry entry = new HandlerEntry<>(address, registration);
      context.addCloseHook(entry);
    }

    boolean newAddress = handlers == actualHandlers;
    return new LocalRegistrationResult<>(holder, newAddress);
  }

  protected <T> void removeRegistration(HandlerHolder<T> holder, Handler<AsyncResult<Void>> completionHandler) {
    boolean last = removeLocalRegistration(holder);
    removeRegistration(last ? holder : null, holder.getHandler().address(), completionHandler);
  }

  protected <T> void removeRegistration(HandlerHolder<T> handlerHolder, String address,
                                        Handler<AsyncResult<Void>> completionHandler) {
    callCompletionHandlerAsync(completionHandler);
  }

  private <T> boolean removeLocalRegistration(HandlerHolder<T> holder) {
    String address = holder.getHandler().address();
    boolean last = handlerMap.compute(address, (key, val) -> {
      if (val == null) {
        return null;
      }
      ConcurrentCyclicSequence<HandlerHolder> next = val.remove(holder);
      return next.size() == 0 ? null : next;
    }) == null;
    if (holder.setRemoved()) {
      holder.getContext().removeCloseHook(new HandlerEntry<>(address, holder.getHandler()));
    }
    return last;
  }

  protected <T> void sendReply(MessageImpl replyMessage, MessageImpl replierMessage, DeliveryOptions options,
                               Handler<AsyncResult<Message<T>>> replyHandler) {
    if (replyMessage.address() == null) {
      throw new IllegalStateException("address not specified");
    } else {
      HandlerRegistration<T> replyHandlerRegistration = createReplyHandlerRegistration(replyMessage, options, replyHandler);
      new ReplySendContextImpl<>(replyMessage, options, replyHandlerRegistration, replierMessage).next();
    }
  }

  protected <T> void sendReply(SendContextImpl<T> sendContext, MessageImpl replierMessage) {
    sendOrPub(sendContext);
  }

  protected <T> void sendOrPub(SendContextImpl<T> sendContext) {
    MessageImpl message = sendContext.message;
    if (metrics != null) {
      metrics.messageSent(message.address(), !message.isSend(), true, false);
    }
    deliverMessageLocally(sendContext);
  }

  protected <T> Handler<Message<T>> convertHandler(Handler<AsyncResult<Message<T>>> handler) {
    return reply -> {
      Future<Message<T>> result;
      if (reply.body() instanceof ReplyException) {
        // This is kind of clunky - but hey-ho
        ReplyException exception = (ReplyException) reply.body();
        if (metrics != null) {
          metrics.replyFailure(reply.address(), exception.failureType());
        }
        result = Future.failedFuture(exception);
      } else {
        result = Future.succeededFuture(reply);
      }
      handler.handle(result);
    };
  }

  protected void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
    if (completionHandler != null) {
      vertx.runOnContext(v -> {
        completionHandler.handle(Future.succeededFuture());
      });
    }
  }

  protected <T> void deliverMessageLocally(SendContextImpl<T> sendContext) {
    if (!deliverMessageLocally(sendContext.message)) {
      // no handlers
      if (metrics != null) {
        metrics.replyFailure(sendContext.message.address, ReplyFailure.NO_HANDLERS);
      }
      if (sendContext.handlerRegistration != null) {
        sendContext.handlerRegistration.sendAsyncResultFailure(ReplyFailure.NO_HANDLERS, "No handlers for address "
                                                               + sendContext.message.address);
      }
    }
  }

  protected boolean isMessageLocal(MessageImpl msg) {
    return true;
  }

  protected <T> boolean deliverMessageLocally(MessageImpl msg) {
    msg.setBus(this);
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
      return true;
    } else {
      if (metrics != null) {
        metrics.messageReceived(msg.address(), !msg.isSend(), isMessageLocal(msg), 0);
      }
      return false;
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

  private <T> HandlerRegistration<T> createReplyHandlerRegistration(MessageImpl message,
                                                                    DeliveryOptions options,
                                                                    Handler<AsyncResult<Message<T>>> replyHandler) {
    if (replyHandler != null) {
      long timeout = options.getSendTimeout();
      String replyAddress = generateReplyAddress();
      message.setReplyAddress(replyAddress);
      Handler<Message<T>> simpleReplyHandler = convertHandler(replyHandler);
      HandlerRegistration<T> registration =
        new HandlerRegistration<>(vertx, metrics, this, replyAddress, message.address, true, replyHandler, timeout);
      registration.handler(simpleReplyHandler);
      return registration;
    } else {
      return null;
    }
  }

  private <T> void sendOrPubInternal(MessageImpl message, DeliveryOptions options,
                                     Handler<AsyncResult<Message<T>>> replyHandler) {
    checkStarted();
    HandlerRegistration<T> replyHandlerRegistration = createReplyHandlerRegistration(message, options, replyHandler);
    SendContextImpl<T> sendContext = new SendContextImpl<>(message, options, replyHandlerRegistration);
    sendContext.next();
  }

  protected class SendContextImpl<T> implements SendContext<T> {

    public final MessageImpl message;
    public final DeliveryOptions options;
    public final HandlerRegistration<T> handlerRegistration;
    public final Iterator<Handler<SendContext>> iter;

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
      return message.isSend();
    }

    @Override
    public Object sentBody() {
      return message.sentBody;
    }
  }

  protected class ReplySendContextImpl<T> extends SendContextImpl<T> {

    private final MessageImpl replierMessage;

    public ReplySendContextImpl(MessageImpl message, DeliveryOptions options, HandlerRegistration<T> handlerRegistration,
                                MessageImpl replierMessage) {
      super(message, options, handlerRegistration);
      this.replierMessage = replierMessage;
    }

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


  private void unregisterAll() {
    // Unregister all handlers explicitly - don't rely on context hooks
    for (ConcurrentCyclicSequence<HandlerHolder> handlers: handlerMap.values()) {
      for (HandlerHolder holder: handlers) {
        holder.getHandler().unregister();
      }
    }
  }

  private <T> void deliverToHandler(MessageImpl msg, HandlerHolder<T> holder) {
    // Each handler gets a fresh copy
    @SuppressWarnings("unchecked")
    Message<T> copied = msg.copyBeforeReceive();

    if (metrics != null) {
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
    close(ar -> {});
    super.finalize();
  }

}

