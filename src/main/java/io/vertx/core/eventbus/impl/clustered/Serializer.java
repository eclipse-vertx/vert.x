/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.impl.OutboundDeliveryContext;
import io.vertx.core.impl.ContextInternal;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * @author Thomas Segismont
 */
public class Serializer {

  private final ContextInternal context;
  private final Map<String, SerializerQueue> queues;

  private Serializer(ContextInternal context) {
    this.context = context;
    queues = new HashMap<>();
    if (context.isDeployment()) {
      context.addCloseHook(this::close);
    }
  }

  public static Serializer get(ContextInternal context) {
    ConcurrentMap<Object, Object> contextData = context.contextData();
    Serializer serializer = (Serializer) contextData.get(Serializer.class);
    if (serializer == null) {
      Serializer candidate = new Serializer(context);
      Serializer previous = (Serializer) contextData.putIfAbsent(Serializer.class, candidate);
      if (previous == null) {
        serializer = candidate;
      } else {
        serializer = previous;
      }
    }
    return serializer;
  }

  public <T> void queue(
    OutboundDeliveryContext<?> sendContext,
    BiConsumer<Message<?>, Promise<T>> selectHandler,
    BiConsumer<OutboundDeliveryContext<?>, T> successHandler,
    BiConsumer<OutboundDeliveryContext<?>, Throwable> failureHandler
  ) {

    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != context) {
      context.runOnContext(v -> queue(sendContext, selectHandler, successHandler, failureHandler));
      return;
    }

    Message<?> message = sendContext.message;
    String address = message.address();

    Promise<T> promise = sendContext.ctx.promise();
    promise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        successHandler.accept(sendContext, ar.result());
      } else {
        failureHandler.accept(sendContext, ar.cause());
      }
    });

    SerializerQueue queue = queues.computeIfAbsent(address, SerializerQueue::new);
    queue.add(new SerializedTask<>(sendContext, selectHandler, promise));
  }

  private void close(Promise<Void> promise) {
    queues.forEach((address, queue) -> queue.close());
    promise.complete();
  }

  private class SerializerQueue {

    final Queue<SerializedTask<?>> tasks;
    final String address;
    boolean closed;

    SerializerQueue(String address) {
      this.address = address;
      tasks = new LinkedList<>();
    }

    void add(SerializedTask<?> serializedTask) {
      tasks.add(serializedTask);
      if (tasks.size() == 1) {
        process(serializedTask);
      }
    }

    void process(SerializedTask<?> serializedTask) {
      Promise<Void> completion = context.promise();
      serializedTask.process(completion);
      completion.future().onComplete(v -> processed());
    }

    void processed() {
      if (!closed) {
        tasks.remove();
        SerializedTask<?> next = tasks.peek();
        if (next != null) {
          process(next);
        } else {
          queues.remove(address);
        }
      }
    }

    void close() {
      closed = true;
      while (!tasks.isEmpty()) {
        tasks.remove().promise.tryFail("Context is closing");
      }
    }
  }

  private class SerializedTask<U> implements Handler<AsyncResult<U>> {

    final OutboundDeliveryContext<?> sendContext;
    final BiConsumer<Message<?>, Promise<U>> selectHandler;
    final Promise<U> promise;
    final Promise<U> internalPromise;
    Promise<Void> completion;

    SerializedTask(
      OutboundDeliveryContext<?> sendContext,
      BiConsumer<Message<?>, Promise<U>> selectHandler,
      Promise<U> promise
    ) {
      this.sendContext = sendContext;
      this.selectHandler = selectHandler;
      this.promise = promise;
      this.internalPromise = context.promise();
      internalPromise.future().onComplete(this);
    }

    void process(Promise<Void> completion) {
      this.completion = completion;
      selectHandler.accept(sendContext.message, internalPromise);
    }

    @Override
    public void handle(AsyncResult<U> ar) {
      if (ar.succeeded()) {
        promise.tryComplete(ar.result());
      } else {
        promise.tryFail(ar.cause());
      }
      completion.complete();
    }
  }
}
