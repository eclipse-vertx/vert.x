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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.streams.DuplexStream;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

import java.util.UUID;

import static io.vertx.core.eventbus.impl.ProducerStream.STREAM_ADDRESS;

public class DuplexStreamImpl<T> implements DuplexStream<T> {

  public static <T> void bind(Vertx vertx, String address, Handler<DuplexStream<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
    EventBus bus = vertx.eventBus();
    bus.consumer(address, msg -> {
      String remoteAddress = msg.headers().get(STREAM_ADDRESS);
      if (remoteAddress != null) {
        String localAddress = UUID.randomUUID().toString();
        DuplexStreamImpl<T> duplex = new DuplexStreamImpl<>(bus, vertx.getOrCreateContext());
        bus.<T>consumer(localAddress, m -> {
          duplex.consumer.handle(m);
          duplex.producer.handle((Message<Object>) m);
        }).completionHandler(ar -> {
          if (ar.succeeded()) {
            duplex.consumer.init(remoteAddress);
            duplex.producer.init(remoteAddress);
            handler.handle(duplex);
            msg.reply(null, new DeliveryOptions().addHeader(STREAM_ADDRESS, localAddress));
          } else {
            msg.fail(0, "go away!");
          }
        });
      } else {
        msg.fail(0, "go away!");
      }
    }).completionHandler(completionHandler);
  }

  public static <T> void open(Vertx vertx, String address, Handler<AsyncResult<DuplexStream<T>>> completionHandler) {
    String localAddress = UUID.randomUUID().toString();
    EventBus bus = vertx.eventBus();
    DuplexStreamImpl<T> duplex = new DuplexStreamImpl<>(bus, vertx.getOrCreateContext());
    bus.<T>consumer(localAddress, msg -> {
      duplex.producer.handle((Message<Object>) msg);
      duplex.consumer.handle(msg);
    }).completionHandler(ar -> {
      if (ar.succeeded()) {
        bus.request(address, null, new DeliveryOptions().addHeader(STREAM_ADDRESS, localAddress), ar2 -> {
          if (ar2.succeeded()) {
            Message<Object> reply = ar2.result();
            String remoteAddress = reply.headers().get(STREAM_ADDRESS);
            duplex.consumer.init(remoteAddress);
            duplex.producer.init(remoteAddress);
            completionHandler.handle(Future.succeededFuture(duplex));
          } else {
            // TODO
          }
        });
      } else {
        //  TODO
      }
    });

  }

  private ConsumerStream<T> consumer;
  private ProducerStream<T> producer;

  public DuplexStreamImpl(EventBus bus, Context context) {
    this.consumer = new ConsumerStream<>(bus, context);
    this.producer = new ProducerStream<>(bus);
  }

  @Override
  public DuplexStream<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public DuplexStream<T> handler(@Nullable Handler<T> handler) {
    consumer.handler(handler);
    return this;
  }

  @Override
  public DuplexStream<T> pause() {
    consumer.pause();
    return this;
  }

  @Override
  public DuplexStream<T> resume() {
    consumer.resume();
    return this;
  }

  @Override
  public DuplexStream<T> fetch(long amount) {
    consumer.fetch(amount);
    return this;
  }

  @Override
  public DuplexStream<T> endHandler(Handler<Void> endHandler) {
    consumer.endHandler(endHandler);
    return this;
  }

  @Override
  public DuplexStream<T> setWriteQueueMaxSize(int maxSize) {
    producer.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public DuplexStream<T> drainHandler(Handler<Void> handler) {
    producer.drainHandler(handler);
    return this;
  }

  @Override
  public Future<Void> write(T data) {
    return producer.write(data);
  }

  @Override
  public void write(T data, Handler<AsyncResult<Void>> handler) {
    producer.write(data, handler);
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    producer.end(handler);
  }

  @Override
  public boolean writeQueueFull() {
    return producer.writeQueueFull();
  }
}
