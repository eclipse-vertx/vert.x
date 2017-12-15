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
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.*;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MessageProducerImpl<T> implements MessageProducer<T> {

  public static final String CREDIT_ADDRESS_HEADER_NAME = "__vertx.credit";

  private final Vertx vertx;
  private final EventBus bus;
  private final boolean send;
  private final String address;
  private final Queue<T> pending = new ArrayDeque<>();
  private final MessageConsumer<Integer> creditConsumer;
  private DeliveryOptions options;
  private int maxSize = DEFAULT_WRITE_QUEUE_MAX_SIZE;
  private int credits = DEFAULT_WRITE_QUEUE_MAX_SIZE;
  private Handler<Void> drainHandler;

  public MessageProducerImpl(Vertx vertx, String address, boolean send, DeliveryOptions options) {
    this.vertx = vertx;
    this.bus = vertx.eventBus();
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
  public MessageProducer<T> send(T message) {
    doSend(message, null);
    return this;
  }

  @Override
  public <R> MessageProducer<T> send(T message, Handler<AsyncResult<Message<R>>> replyHandler) {
    doSend(message, replyHandler);
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
  public synchronized MessageProducer<T> write(T data) {
    if (send) {
      doSend(data, null);
    } else {
      bus.publish(address, data, options);
    }
    return this;
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
  public void end() {
    close();
  }

  @Override
  public void close() {
    if (creditConsumer != null) {
      creditConsumer.unregister();
    }
  }

  // Just in case user forget to call close()
  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  private synchronized <R> void doSend(T data, Handler<AsyncResult<Message<R>>> replyHandler) {
    if (credits > 0) {
      credits--;
      if (replyHandler == null) {
        bus.send(address, data, options);
      } else {
        bus.send(address, data, options, replyHandler);
      }
    } else {
      pending.add(data);
    }
  }

  private synchronized void doReceiveCredit(int credit) {
    credits += credit;
    while (credits > 0) {
      T data = pending.poll();
      if (data == null) {
        break;
      } else {
        credits--;
        bus.send(address, data, options);
      }
    }
    checkDrained();
  }
}
