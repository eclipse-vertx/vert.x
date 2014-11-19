/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MessageProducerImpl<T> implements MessageProducer<T> {

  private final EventBus bus;
  private final boolean send;
  private final String address;
  private DeliveryOptions options;

  public MessageProducerImpl(EventBus bus, String address, boolean send, DeliveryOptions options) {
    this.bus = bus;
    this.address = address;
    this.send = send;
    this.options = options;
  }

  @Override
  public synchronized MessageProducer<T> deliveryOptions(DeliveryOptions options) {
    this.options = options;
    return this;
  }

  @Override
  public MessageProducer<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public MessageProducer<T> setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public synchronized MessageProducer<T> write(T data) {
    if (send) {
      bus.send(address, data, options);
    } else {
      bus.publish(address, data, options);
    }
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return false;
  }

  @Override
  public MessageProducer<T> drainHandler(Handler<Void> handler) {
    return this;
  }

  @Override
  public String address() {
    return address;
  }

}
