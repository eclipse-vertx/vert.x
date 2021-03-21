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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.streams.WriteStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ProducerStream<T> implements WriteStream<T>, Handler<Message<Object>> {

  static final String STREAM_CREDITS = "_______vertx.stream.credits"; // To not collide with was is built in consumer
  static final String STREAM_ADDRESS = "_______vertx.stream.address"; // To not collide with was is built in consumer

  public static <T> void open(EventBus eventBus, String address, Handler<AsyncResult<WriteStream<T>>> handler) {
    String localAddress = UUID.randomUUID().toString();
    ProducerStream<T> stream = new ProducerStream<>(eventBus);
    eventBus.consumer(localAddress, stream).completionHandler(ar1 -> {
      if (ar1.succeeded()) {
        eventBus.request(address, null, new DeliveryOptions().addHeader(STREAM_ADDRESS, localAddress), ar2 -> {
          if (ar2.succeeded()) {
            stream.address = ar2.result().headers().get(STREAM_ADDRESS);
            handler.handle(Future.succeededFuture(stream));
          } else {
            handler.handle(Future.failedFuture(ar2.cause()));
          }
        });
      } else {
        handler.handle(Future.failedFuture(ar1.cause()));
      }
    });
  }

  public static <T> void bind(EventBus bus, String address, Handler<WriteStream<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
    bus.consumer(address, msg -> {
      String remoteAddress = msg.headers().get(STREAM_ADDRESS);
      if (remoteAddress == null) {
        msg.fail(0, "no stream address");
      } else {
        String localAddress = UUID.randomUUID().toString();
        ProducerStream<T> stream = new ProducerStream<>(bus);
        stream.address = remoteAddress;
        bus.consumer(localAddress, stream).completionHandler(ar -> {
          if (ar.succeeded()) {
            handler.handle(stream);
            msg.reply(null, new DeliveryOptions().addHeader(STREAM_CREDITS, localAddress));
          } else {
            msg.fail(0, "internal error");
            completionHandler.handle(Future.failedFuture(ar.cause()));
          }
        });
      }
    }).completionHandler(completionHandler);
  }

  private final EventBus bus;
  private String address;
  private int credits = 1000;
  private Deque<Msg<T>> pending = new ArrayDeque<>();
  private Handler<Void> drainHandler;

  ProducerStream(EventBus bus) {
    this.bus = bus;
  }

  void init(String address) {
    this.address = address;
  }

  @Override
  public synchronized void handle(Message<Object> refundMsg) {
    String s = refundMsg.headers().get(STREAM_CREDITS);
    if (s != null) {
      int amount = Integer.parseInt(s);
      boolean full = credits == 0;
      credits += amount;
      Msg<T> msg;
      while (credits > 0 && (msg = pending.poll()) != null) {
        bus.send(address, msg.body);
        msg.handler.handle(Future.succeededFuture());
      }
      if (full && credits > 0) {
        if (drainHandler != null) {
          drainHandler.handle(null);
        }
      }
    }
  }

  @Override
  public WriteStream<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  private static class Msg<T> {
    final T body;
    final Handler<AsyncResult<Void>> handler;
    Msg(T body, Handler<AsyncResult<Void>> handler) {
      this.body = body;
      this.handler = handler;
    }
  }

  @Override
  public Future<Void> write(T data) {
    Promise<Void> p = Promise.promise();
    write(data, p);
    return p.future();
  }

  @Override
  public synchronized void write(T data, Handler<AsyncResult<Void>> handler) {
    if (credits > 0) {
      credits--;
      bus.send(address, data);
      handler.handle(Future.succeededFuture()); // Not correct
    } else {
      pending.add(new Msg<>(data, handler));
    }
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    // TODO
  }

  @Override
  public WriteStream<T> setWriteQueueMaxSize(int maxSize) {
    // TODO
    return this;
  }

  @Override
  public synchronized boolean writeQueueFull() {
    // TODO
    return credits == 0;
  }

  @Override
  public synchronized WriteStream<T> drainHandler(@Nullable Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }
}
