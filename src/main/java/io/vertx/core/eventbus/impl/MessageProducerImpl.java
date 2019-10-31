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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MessageProducerImpl<T> implements MessageProducer<T> {

  public static final String CREDIT_ADDRESS_HEADER_NAME = "__vertx.credit";

  private final Vertx vertx;
  private final EventBusImpl bus;
  private final boolean send;
  private final String address;
  private final Queue<OutboundDeliveryContext<T>> pending = new ArrayDeque<>();
  private final MessageConsumer<Integer> creditConsumer;
  private DeliveryOptions options;
  private int maxSize = DEFAULT_WRITE_QUEUE_MAX_SIZE;
  private int credits = DEFAULT_WRITE_QUEUE_MAX_SIZE;
  private Handler<Void> drainHandler;

  public MessageProducerImpl(Vertx vertx, String address, boolean send, DeliveryOptions options) {
    this.vertx = vertx;
    this.bus = (EventBusImpl) vertx.eventBus();
    this.address = address;
    this.send = send;
    this.options = options;
    if (send) {
      String creditAddress = UUID.randomUUID().toString() + "-credit";
      creditConsumer = bus.consumer(creditAddress, msg -> {
        doReceiveCredit(msg.body());
      });
      options.addHeader(CREDIT_ADDRESS_HEADER_NAME, creditAddress);
    } else {
      creditConsumer = null;
    }
  }

  @Override
  public synchronized MessageProducer<T> deliveryOptions(DeliveryOptions options) {
    if (creditConsumer != null) {
      options = new DeliveryOptions(options);
      options.addHeader(CREDIT_ADDRESS_HEADER_NAME, this.options.getHeaders().get(CREDIT_ADDRESS_HEADER_NAME));
    }
    this.options = options;
    return this;
  }

  @Override
  public MessageProducer<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public synchronized MessageProducer<T> setWriteQueueMaxSize(int s) {
    int delta = s - maxSize;
    maxSize = s;
    credits += delta;
    return this;
  }

  @Override
  public synchronized Future<Void> write(T data) {
    Promise<Void> promise = ((VertxInternal)vertx).getOrCreateContext().promise();
    write(data, promise);
    return promise.future();
  }

  @Override
  public void write(T data, Handler<AsyncResult<Void>> handler) {
    Promise<Void> promise = null;
    if (handler != null) {
      promise = ((VertxInternal)vertx).getOrCreateContext().promise();
      promise.future().setHandler(handler);
    }
    write(data, promise);
  }

  private void write(T data, Promise<Void> handler) {
    MessageImpl msg = bus.createMessage(send, address, options.getHeaders(), data, options.getCodecName());
    OutboundDeliveryContext<T> sendCtx = bus.newSendContext(msg, options, null, handler);
    if (send) {
      synchronized (this) {
        if (credits > 0) {
          credits--;
        } else {
          pending.add(sendCtx);
          return;
        }
      }
    }
    bus.sendOrPubInternal(msg, options, null, handler);
  }

  @Override
  public synchronized boolean writeQueueFull() {
    return credits == 0;
  }

  @Override
  public synchronized MessageProducer<T> drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    if (handler != null) {
      checkDrained();
    }
    return this;
  }

  private void checkDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null && credits >= maxSize / 2) {
      this.drainHandler = null;
      vertx.runOnContext(v -> handler.handle(null));
    }
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public Future<Void> end() {
    return close();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    close(null);
  }

  @Override
  public Future<Void> close() {
    if (creditConsumer != null) {
      return creditConsumer.unregister();
    } else {
      return ((ContextInternal)vertx.getOrCreateContext()).succeededFuture();
    }
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = close();
    if (handler != null) {
      fut.setHandler(handler);
    }
  }

  // Just in case user forget to call close()
  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  private synchronized void doReceiveCredit(int credit) {
    credits += credit;
    while (credits > 0) {
      OutboundDeliveryContext<T> sendContext = pending.poll();
      if (sendContext == null) {
        break;
      } else {
        credits--;
        bus.sendOrPubInternal(sendContext);
      }
    }
    checkDrained();
  }
}
