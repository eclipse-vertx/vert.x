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
package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.impl.ConsumerStream;
import io.vertx.core.eventbus.impl.DuplexStreamImpl;
import io.vertx.core.eventbus.impl.ProducerStream;
import io.vertx.core.streams.DuplexStream;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

@VertxGen
public interface EventBusStream {


  static <T> void bindDuplex(Vertx vertx, String address, Handler<DuplexStream<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
    DuplexStreamImpl.bind(vertx, address, handler, completionHandler);
  }

  static <T> void openDuplex(Vertx vertx, String address, Handler<AsyncResult<DuplexStream<T>>> completionHandler) {
    DuplexStreamImpl.open(vertx, address, completionHandler);
  }

  static <T> void openProducer(EventBus bus, String address, Handler<AsyncResult<WriteStream<T>>> handler) {
    ProducerStream.open(bus, address, handler);
  }

  static <T> void bindProducer(EventBus bus, String address, Handler<WriteStream<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
    ProducerStream.bind(bus, address, handler, completionHandler);
  }

  static <T> void bindConsumer(Vertx vertx, String address, Handler<ReadStream<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
    ConsumerStream.bind(vertx, address, handler, completionHandler);
  }

  static <T> void openConsumer(Vertx vertx, String address, Handler<AsyncResult<ReadStream<T>>> completionHandler) {
    ConsumerStream.open(vertx, address, completionHandler);
  }
}
