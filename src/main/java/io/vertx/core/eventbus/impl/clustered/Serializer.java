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

import io.netty.channel.EventLoop;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
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

  public <T> void queue(Message<?> message, BiConsumer<Message<?>, Promise<T>> selectHandler, Promise<T> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      String address = message.address();
      SerializerQueue queue = queues.computeIfAbsent(address, SerializerQueue::new);
      queue.add(message, selectHandler, promise);
    } else {
      eventLoop.execute(() -> queue(message, selectHandler, promise));
    }
  }

  private void close(Promise<Void> promise) {
    queues.forEach((address, queue) -> queue.close());
    promise.complete();
  }

  private class SerializerQueue {

    private final Queue<SerializedTask<?>> tasks;
    private final String address;
    private boolean running;
    private boolean closed;

    SerializerQueue(String address) {
      this.address = address;
      this.tasks = new LinkedList<>();
    }

    void checkPending() {
      if (!running) {
        running = true;
        while (true) {
          SerializedTask<?> task = tasks.peek();
          if (task != null) {
            task.process();
            if (tasks.peek() == task) {
              // Task will be completed later
              break;
            }
          } else {
            queues.remove(address);
            break;
          }
        }
        running = false;
      }
    }

    <U> void add(Message<?> msg, BiConsumer<Message<?>, Promise<U>> selectHandler, Promise<U> promise) {
      SerializedTask<U> serializedTask = new SerializedTask<>(context, msg, selectHandler);
      Future<U> fut = serializedTask.internalPromise.future();
      fut.onComplete(promise);
      fut.onComplete(serializedTask);
      tasks.add(serializedTask);
      checkPending();
    }

    void processed() {
      if (!closed) {
        tasks.poll();
        checkPending();
      }
    }

    void close() {
      closed = true;
      while (!tasks.isEmpty()) {
        tasks.remove().internalPromise.tryFail("Context is closing");
      }
    }

    private class SerializedTask<U> implements Handler<AsyncResult<U>> {

      final Message<?> msg;
      final BiConsumer<Message<?>, Promise<U>> selectHandler;
      final Promise<U> internalPromise;

      SerializedTask(
        ContextInternal context,
        Message<?> msg,
        BiConsumer<Message<?>, Promise<U>> selectHandler) {
        this.msg = msg;
        this.selectHandler = selectHandler;
        this.internalPromise = context.promise();
      }

      void process() {
        selectHandler.accept(msg, internalPromise);
      }

      @Override
      public void handle(AsyncResult<U> ar) {
        processed();
      }
    }
  }
}
