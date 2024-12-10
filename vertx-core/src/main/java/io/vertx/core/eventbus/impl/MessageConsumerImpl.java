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
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;

import java.util.*;

/*
 */
public class MessageConsumerImpl<T> extends HandlerRegistration<T> implements MessageConsumer<T> {

  private static final Logger log = LoggerFactory.getLogger(MessageConsumerImpl.class);

  private static final int DEFAULT_MAX_BUFFERED_MESSAGES = 1000;

  private final boolean localOnly;
  private Handler<Message<T>> handler;
  private Handler<Void> endHandler;
  private Handler<Message<T>> discardHandler;
  private int maxBufferedMessages = DEFAULT_MAX_BUFFERED_MESSAGES;
  private Queue<Message<T>> pending = new ArrayDeque<>(8);
  private long demand = Long.MAX_VALUE;
  private Promise<Void> result;
  private boolean registered;

  MessageConsumerImpl(ContextInternal context, EventBusImpl eventBus, String address, boolean localOnly) {
    super(context, eventBus, address, false);
    this.localOnly = localOnly;
    this.result = context.promise();
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
      if (pending.isEmpty()) {
        return this;
      }
      discardHandler = this.discardHandler;
      discarded = new ArrayList<>(overflow);
      while (pending.size() > maxBufferedMessages) {
        discarded.add(pending.poll());
      }
    }
    for (Message<T> msg : discarded) {
      if (discardHandler != null) {
        discardHandler.handle(msg);
      }
      discard(msg);
    }
    return this;
  }

  @Override
  public synchronized int getMaxBufferedMessages() {
    return maxBufferedMessages;
  }

  @Override
  public synchronized Future<Void> completion() {
    return result.future();
  }

  @Override
  public synchronized Future<Void> unregister() {
    handler = null;
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (pending.size() > 0) {
      Queue<Message<T>> discarded = pending;
      Handler<Message<T>> handler = discardHandler;
      pending = new ArrayDeque<>(8);
      for (Message<T> msg : discarded) {
        discard(msg);
        if (handler != null) {
          context.emit(msg, handler);
        }
      }
    }
    discardHandler = null;
    Future<Void> fut = super.unregister();
    if (registered) {
      registered = false;
      Promise<Void> res = result; // Alias reference because result can become null when the onComplete callback executes
      fut.onComplete(ar -> res.tryFail("Consumer unregistered before registration completed"));
      result = context.promise();
    }
    return fut;
  }

  protected boolean doReceive(Message<T> message) {
    Handler<Message<T>> theHandler;
    synchronized (this) {
      if (handler == null) {
        return false;
      }
      if (demand == 0L) {
        if (pending.size() < maxBufferedMessages) {
          pending.add(message);
          return true;
        } else {
          discard(message);
          if (discardHandler != null) {
            discardHandler.handle(message);
          } else {
            log.warn("Discarding message as more than " + maxBufferedMessages + " buffered in paused consumer. address: " + address);
          }
        }
        return true;
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
    }
    deliver(theHandler, message);
    return true;
  }

  @Override
  protected void dispatch(Message<T> msg, ContextInternal context, Handler<Message<T>> handler) {
    if (handler == null) {
      throw new NullPointerException();
    }
    context.dispatch(msg, handler);
  }

  private void deliver(Handler<Message<T>> theHandler, Message<T> message) {
    // Handle the message outside the sync block
    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=473714
    dispatch(theHandler, message, context.duplicate());
    checkNextTick();
  }

  private synchronized void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!pending.isEmpty() && demand > 0L) {
      context.nettyEventLoop().execute(() -> {
        Message<T> message;
        Handler<Message<T>> theHandler;
        synchronized (MessageConsumerImpl.this) {
          if (demand == 0L || (message = pending.poll()) == null) {
            return;
          }
          if (demand != Long.MAX_VALUE) {
            demand--;
          }
          theHandler = handler;
        }
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

  @Override
  public synchronized MessageConsumer<T> handler(Handler<Message<T>> h) {
    if (h != null) {
      synchronized (this) {
        handler = h;
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
    } else {
      unregister();
    }
    return this;
  }

  @Override
  public ReadStream<T> bodyStream() {
    return new BodyReadStream<>(this);
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

  public synchronized Handler<Message<T>> getHandler() {
    return handler;
  }
}
