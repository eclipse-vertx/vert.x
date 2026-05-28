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

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 */
public class MessageConsumerImpl<T> extends HandlerRegistration<T> implements MessageConsumer<T> {

  private static final Logger log = LoggerFactory.getLogger(MessageConsumerImpl.class);

  private final boolean localOnly;
  private Function<Message<T>, Future<Void>> processor;
  private Handler<Void> endHandler;
  private Handler<Message<T>> discardHandler;
  private final int maxBufferedMessages;
  private final InboundMessageQueue<Message<T>> pending;
  private Promise<Void> result;
  private boolean registered;
  private boolean full;

  MessageConsumerImpl(ContextInternal context, EventBusImpl eventBus, String address, boolean localOnly, int maxBufferedMessages) {
    super(context, eventBus, address, false);
    this.localOnly = localOnly;
    this.result = context.promise();
    this.maxBufferedMessages = maxBufferedMessages;
    this.pending = new InboundMessageQueue<>(context.executor(), context.executor(), maxBufferedMessages, maxBufferedMessages) {
      @Override
      protected void handleResume() {
        full = false;
      }
      @Override
      protected void handlePause() {
        full = true;
      }
      @Override
      protected void handleMessage(Message<T> msg) {
        Function<Message<T>, Future<Void>> processor;
        synchronized (MessageConsumerImpl.this) {
          processor = MessageConsumerImpl.this.processor;
        }
        if (processor != null) {
          dispatchMessage(processor, (MessageImpl<?, T>) msg, context.duplicate());
        } else {
          handleDiscard(msg, false);
        }
      }

      @Override
      protected void handleDispose(Message<T> msg) {
        handleDiscard(msg, false);
      }
    };
  }

  @Override
  public synchronized Future<Void> completion() {
    return result.future();
  }

  @Override
  public synchronized Future<Void> unregister() {
    processor = null;
    if (endHandler != null) {
      endHandler.handle(null);
    }
    pending.close();
    Future<Void> fut = super.unregister();
    if (registered) {
      registered = false;
      Promise<Void> res = result; // Alias reference because result can become null when the onComplete callback executes
      fut.onComplete(ar -> res.tryFail("Consumer unregistered before registration completed"));
      result = context.promise();
    }
    return fut;
  }

  private void handleDiscard(Message<T> message, boolean isFull) {
    if (discardHandler != null) {
      context.dispatch(message, discardHandler);
    } else if (isFull) {
      if (log.isWarnEnabled()) {
        log.warn("Discarding message as more than " + maxBufferedMessages + " buffered in paused consumer. address: " + address);
      }
    } else {
      if (log.isWarnEnabled()) {
        log.warn("Discarding message since the consumer is not registered. address: " + address);
      }
    }

    // Cleanup message
    discardMessage(message);
  }

  protected void doReceive(Message<T> message) {
    if (full) {
      handleDiscard(message, true);
    } else {
      pending.write(message);
    }
  }

  @Override
  protected void dispatchMessage(Message<T> msg, ContextInternal context, Function<Message<T>, Future<Void>> processor, Completable<Void> completion) {
    if (processor == null) {
      throw new NullPointerException();
    }
    context.dispatch(msg, message -> {
      try {
        Future<Void> future = processor.apply(message);
        if (future == null) {
          completion.fail(new NullPointerException("processor returned null"));
        } else {
          future.onComplete(completion);
        }
      } catch (Exception e) {
        completion.fail(e);
      }
    });
  }

  /*
   * Internal API for testing purposes, handle dropped messages instead of logging them.
   */
  public synchronized void discardHandler(Handler<Message<T>> handler) {
    this.discardHandler = handler;
  }

  @Override
  public synchronized MessageConsumer<T> handler(Handler<Message<T>> h) {
    if (h != null) {
      processor = new HandlerAdapter<>(h);
      registerIfNeeded();
    } else {
      unregister();
    }
    return this;
  }

  private static class HandlerAdapter<BODY_TYPE> implements Function<Message<BODY_TYPE>, Future<Void>> {
    final Handler<Message<BODY_TYPE>> h;

    HandlerAdapter(Handler<Message<BODY_TYPE>> h) {
      this.h = h;
    }

    @Override
    public Future<Void> apply(Message<BODY_TYPE> msg) {
      h.handle(msg);
      return Future.succeededFuture();
    }
  }

  @Override
  public synchronized MessageConsumer<T> processor(Function<Message<T>, Future<Void>> processor) {
    if (processor != null) {
      this.processor = processor;
      registerIfNeeded();
    } else {
      unregister();
    }
    return this;
  }

  private void registerIfNeeded() {
    if (!registered) {
      registered = true;
      Promise<Void> p = result;
      Promise<Void> registration = context.promise();
      register(true, localOnly, registration);
      registration.future().onComplete(ar -> {
        if (ar.succeeded()) {
          p.tryComplete();
        } else {
          p.tryFail(ar.cause());
        }
      });
    }
  }

  @Override
  public ReadStream<T> bodyStream() {
    return new BodyReadStream<>(this);
  }

  @Override
  public synchronized MessageConsumer<T> pause() {
    pending.pause();
    return this;
  }

  @Override
  public MessageConsumer<T> resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public synchronized MessageConsumer<T> fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public synchronized MessageConsumer<T> endHandler(Handler<Void> endHandler) {
    if (endHandler != null) {
      // We should use the HandlerHolder context to properly do this (needs small refactoring)
      Context endCtx = context.owner().getOrCreateContext();
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
}
