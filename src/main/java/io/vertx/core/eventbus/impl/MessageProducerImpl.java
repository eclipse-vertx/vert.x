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

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MessageProducerImpl<T> implements MessageProducer<T> {

  private final Vertx vertx;
  private final EventBusImpl bus;
  private final boolean send;
  private final String address;
  private DeliveryOptions options;

  public MessageProducerImpl(Vertx vertx, String address, boolean send, DeliveryOptions options) {
    this.vertx = vertx;
    this.bus = (EventBusImpl) vertx.eventBus();
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
  public Future<Void> write(T body) {
    MessageImpl msg = bus.createMessage(send, address, options.getHeaders(), body, options.getCodecName());
    return bus.sendOrPubInternal(msg, options, null);
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public Future<Void> close() {
    return ((ContextInternal)vertx.getOrCreateContext()).succeededFuture();
  }

}
