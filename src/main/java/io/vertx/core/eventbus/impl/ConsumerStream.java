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
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.InboundBuffer;

import java.util.UUID;

import static io.vertx.core.eventbus.impl.ProducerStream.STREAM_ADDRESS;
import static io.vertx.core.eventbus.impl.ProducerStream.STREAM_CREDITS;

public class ConsumerStream<T> implements ReadStream<T>, Handler<Message<T>>  {

  public static <T> void bind(Vertx vertx, String address, Handler<ReadStream<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
    Context context = vertx.getOrCreateContext();
    Handler<ReadStream<T>> h = handler;
    EventBus bus = context.owner().eventBus();
    bus.consumer(address, msg -> {
      String remoteAddress = msg.headers().get(STREAM_ADDRESS);
      if (remoteAddress == null) {
        // No back pressure (not supported)
        msg.fail(0, "No credits address");
        return;
      }
      String localAddress = UUID.randomUUID().toString();
      ConsumerStream<T> stream = new ConsumerStream<>(bus, context);
      stream.init(remoteAddress);
      bus.consumer(localAddress, stream).completionHandler(ar -> {
        if (ar.succeeded()) {
          h.handle(stream);
          msg.reply(null, new DeliveryOptions().addHeader(STREAM_ADDRESS, localAddress));
        } else {
          msg.fail(0, ar.cause().getMessage());
        }
      });
    }).completionHandler(completionHandler);
  }

  public static <T> void open(Vertx vertx, String address, Handler<AsyncResult<ReadStream<T>>> completionHandler) {
    Context context = vertx.getOrCreateContext();
    EventBus bus = vertx.eventBus();
    String localAddress = UUID.randomUUID().toString();
    ConsumerStream<T> consumer = new ConsumerStream<>(bus, context);
    MessageConsumer<T> localConsumer = bus.consumer(localAddress, consumer);
    localConsumer.completionHandler(ar -> {
      if (ar.succeeded()) {
        bus.request(address, null, new DeliveryOptions().addHeader(STREAM_ADDRESS, localAddress), ar2 -> {
          if (ar2.succeeded()) {
            Message<Object> msg = ar2.result();
            String remoteAddress = msg.headers().get(ProducerStream.STREAM_CREDITS);
            if (remoteAddress == null) {
              localConsumer.unregister(v -> completionHandler.handle(Future.failedFuture("Handshake failure")));
            } else {
              completionHandler.handle(Future.succeededFuture(consumer));
              consumer.init(remoteAddress);
            }
          } else {
            localConsumer.unregister(v -> completionHandler.handle(Future.failedFuture(ar2.cause())));
          }
        });
      } else {
        completionHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  private final EventBus bus;
  private InboundBuffer<Message<T>> inboundBuffer;
  private Handler<T> handler;
  private long credits = 0;

  ConsumerStream(EventBus bus, Context context) {
    this.bus = bus;
    this.inboundBuffer = new InboundBuffer<Message<T>>(context).pause();
  }

  void init(String remoteAddress) {
    inboundBuffer.handler(msg -> {
      Handler<T> handler;
      boolean refund;
      synchronized (this) {
        handler = this.handler;
        credits++;
        if (credits > 500) {
          credits = 0;
          refund = true;
        } else {
          refund = false;
        }
      }
      if (handler != null) {
        handler.handle(msg.body());
      }
      if (refund) {
        // Refund 500 credits at once
        bus.send(remoteAddress, null, new DeliveryOptions().addHeader(STREAM_CREDITS, "500"));
      }
    }).resume();
  }

  @Override
  public void handle(Message<T> message) {
    inboundBuffer.write(message);
  }

  @Override
  public synchronized ReadStream<T> handler(@Nullable Handler<T> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public ReadStream<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public ReadStream<T> pause() {
    inboundBuffer.pause();
    return this;
  }

  @Override
  public ReadStream<T> resume() {
    inboundBuffer.resume();
    return this;
  }

  @Override
  public ReadStream<T> fetch(long amount) {
    inboundBuffer.fetch(amount);
    return this;
  }

  @Override
  public ReadStream<T> endHandler(@Nullable Handler<Void> endHandler) {
    return this;
  }
}
