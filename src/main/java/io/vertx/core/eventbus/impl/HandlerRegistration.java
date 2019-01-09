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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.streams.ReadStream;

import java.util.*;

/*
 * This class is optimised for performance when used on the same event loop it was created on.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 */
public class HandlerRegistration<T> implements MessageConsumer<T>, Handler<Message<T>> {

  private static final Logger log = LoggerFactory.getLogger(HandlerRegistration.class);

  public static final int DEFAULT_MAX_BUFFERED_MESSAGES = 1000;

  private final Vertx vertx;
  private final EventBusMetrics metrics;
  private final EventBusImpl eventBus;
  private final String address;
  private final String repliedAddress;
  private final boolean localOnly;
  private long timeoutID = -1;
  private HandlerHolder<T> registered;
  private Handler<Message<T>> handler;
  private ContextInternal handlerContext;
  private AsyncResult<Void> result;
  private Handler<AsyncResult<Void>> completionHandler;
  private Handler<Void> endHandler;
  private Handler<Message<T>> discardHandler;
  private int maxBufferedMessages = DEFAULT_MAX_BUFFERED_MESSAGES;
  private final Queue<Message<T>> pending = new ArrayDeque<>(8);
  private long demand = Long.MAX_VALUE;
  private Object metric;

  public HandlerRegistration(Vertx vertx, EventBusMetrics metrics, EventBusImpl eventBus, String address,
                             String repliedAddress, boolean localOnly, long timeout) {
    this.vertx = vertx;
    this.metrics = metrics;
    this.eventBus = eventBus;
    this.address = address;
    this.repliedAddress = repliedAddress;
    this.localOnly = localOnly;
    if (timeout != -1) {
      timeoutID = vertx.setTimer(timeout, tid -> {
        sendAsyncResultFailure(ReplyFailure.TIMEOUT, "Timed out after waiting " + timeout + "(ms) for a reply. address: " + address + ", repliedAddress: " + repliedAddress);
      });
    }
  }

  @Override
  public MessageConsumer<T> setMaxBufferedMessages(int maxBufferedMessages) {
    Arguments.require(maxBufferedMessages >= 0, "Max buffered messages cannot be negative");
    List<Message<T>> discarded;
    Handler<Message<T>> discardHandler;
    synchronized (this) {
      this.maxBufferedMessages = maxBufferedMessages;
      int overflow = pending.size() - maxBufferedMessages;
      if (overflow <= 0) {
        return this;
      }
      discardHandler = this.discardHandler;
      if (discardHandler == null) {
        while (pending.size() > maxBufferedMessages) {
          pending.poll();
        }
        return this;
      }
      discarded = new ArrayList<>(overflow);
      while (pending.size() > maxBufferedMessages) {
        discarded.add(pending.poll());
      }
    }
    for (Message<T> msg : discarded) {
      discardHandler.handle(msg);
    }
    return this;
  }

  @Override
  public synchronized int getMaxBufferedMessages() {
    return maxBufferedMessages;
  }

  @Override
  public String address() {
    return address;
  }

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

  @Override
  public void unregister() {
    doUnregister(null);
  }

  @Override
  public void unregister(Handler<AsyncResult<Void>> completionHandler) {
    Objects.requireNonNull(completionHandler);
    doUnregister(completionHandler);
  }

  public void sendAsyncResultFailure(ReplyFailure failure, String msg) {
    unregister();
    MessageImpl timeout = new MessageImpl(repliedAddress, null, new CaseInsensitiveHeaders(), new ReplyException(failure, msg), CodecManager.REPLY_EXCEPTION_MESSAGE_CODEC, true, eventBus);
    handler.handle(timeout);
  }

  private void doUnregister(Handler<AsyncResult<Void>> completionHandler) {
    Deque<Message<T>> discarded;
    Handler<Message<T>> discardHandler;
    synchronized (this) {
      if (timeoutID != -1) {
        vertx.cancelTimer(timeoutID);
      }
      if (endHandler != null) {
        Handler<Void> theEndHandler = endHandler;
        Handler<AsyncResult<Void>> handler = completionHandler;
        completionHandler = ar -> {
          theEndHandler.handle(null);
          if (handler != null) {
            handler.handle(ar);
          }
        };
      }
      HandlerHolder<T> holder = registered;
      if (pending.size() > 0) {
        discarded = new ArrayDeque<>(pending);
        pending.clear();
      } else {
        discarded = null;
      }
      discardHandler = this.discardHandler;
      if (holder != null) {
        registered = null;
        eventBus.removeRegistration(holder, completionHandler);
      } else {
        callCompletionHandlerAsync(completionHandler);
      }
    }
    if (discardHandler != null && discarded != null) {
      Message<T> msg;
      while ((msg = discarded.poll()) != null) {
        discardHandler.handle(msg);
      }
    }
  }

  private void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
    if (completionHandler != null) {
      vertx.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
    }
  }

  synchronized void setHandlerContext(Context context) {
    handlerContext = (ContextInternal) context;
  }

  public synchronized void setResult(AsyncResult<Void> result) {
    this.result = result;
    if (completionHandler != null) {
      if (metrics != null && result.succeeded()) {
        metric = metrics.handlerRegistered(address, repliedAddress);
      }
      Handler<AsyncResult<Void>> callback = completionHandler;
      vertx.runOnContext(v -> callback.handle(result));
    } else if (result.failed()) {
      log.error("Failed to propagate registration for handler " + handler + " and address " + address);
    } else if (metrics != null) {
      metric = metrics.handlerRegistered(address, repliedAddress);
    }
  }

  @Override
  public void handle(Message<T> message) {
    Handler<Message<T>> theHandler;
    ContextInternal ctx;
    synchronized (this) {
      if (demand == 0L) {
        if (pending.size() < maxBufferedMessages) {
          pending.add(message);
        } else {
          if (discardHandler != null) {
            discardHandler.handle(message);
          } else {
            log.warn("Discarding message as more than " + maxBufferedMessages + " buffered in paused consumer. address: " + address);
          }
        }
        return;
      } else {
        if (pending.size() > 0) {
          pending.add(message);
          message = pending.poll();
        }
        if (demand != Long.MAX_VALUE) {
          demand--;
        }
        theHandler = handler;
      }
      ctx = handlerContext;
    }
    deliver(theHandler, message, ctx);
  }

  private void deliver(Handler<Message<T>> theHandler, Message<T> message, ContextInternal context) {
    // Handle the message outside the sync block
    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=473714
    boolean local = true;
    if (message instanceof ClusteredMessage) {
      // A bit hacky
      ClusteredMessage cmsg = (ClusteredMessage)message;
      if (cmsg.isFromWire()) {
        local = false;
      }
    }
    String creditsAddress = message.headers().get(MessageProducerImpl.CREDIT_ADDRESS_HEADER_NAME);
    if (creditsAddress != null) {
      eventBus.send(creditsAddress, 1);
    }
    try {
      if (metrics != null) {
        metrics.beginHandleMessage(metric, local);
      }
      theHandler.handle(message);
      if (metrics != null) {
        metrics.endHandleMessage(metric, null);
      }
    } catch (Exception e) {
      log.error("Failed to handleMessage. address: " + message.address(), e);
      if (metrics != null) {
        metrics.endHandleMessage(metric, e);
      }
      context.reportException(e);
    }
    checkNextTick();
  }

  private synchronized void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!pending.isEmpty() && demand > 0L) {
      handlerContext.runOnContext(v -> {
        Message<T> message;
        Handler<Message<T>> theHandler;
        ContextInternal ctx;
        synchronized (HandlerRegistration.this) {
          if (demand == 0L || (message = pending.poll()) == null) {
            return;
          }
          if (demand != Long.MAX_VALUE) {
            demand--;
          }
          theHandler = handler;
          ctx = handlerContext;
        }
        deliver(theHandler, message, ctx);
      });
    }
  }

  /*
   * Internal API for testing purposes.
   */
  public synchronized void discardHandler(Handler<Message<T>> handler) {
    this.discardHandler = handler;
  }

  @Override
  public synchronized MessageConsumer<T> handler(Handler<Message<T>> handler) {
    this.handler = handler;
    if (this.handler != null && registered == null) {
      registered = eventBus.addRegistration(address, this, repliedAddress != null, localOnly);
    } else if (this.handler == null && registered != null) {
      // This will set registered to false
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
    return registered != null;
  }

  @Override
  public synchronized MessageConsumer<T> pause() {
    demand = 0L;
    return this;
  }

  @Override
  public MessageConsumer<T> resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public synchronized MessageConsumer<T> fetch(long amount) {
    if (amount < 0) {
      throw new IllegalArgumentException();
    }
    demand += amount;
    if (demand < 0L) {
      demand = Long.MAX_VALUE;
    }
    if (demand > 0L) {
      checkNextTick();
    }
    return this;
  }

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
