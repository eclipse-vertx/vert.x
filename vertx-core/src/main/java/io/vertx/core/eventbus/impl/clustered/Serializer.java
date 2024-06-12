/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * @author Thomas Segismont
 */
public class Serializer implements Closeable {

  private final ContextInternal ctx;
  private final Map<String, SerializerQueue> queues;

  private Serializer(ContextInternal context) {
    ContextInternal unwrapped = context.unwrap();
    if (unwrapped.isEventLoopContext()) {
      ctx = unwrapped;
    } else {
      VertxInternal vertx = unwrapped.owner();
      ctx = vertx.createEventLoopContext(unwrapped.nettyEventLoop(), unwrapped.workerPool(), unwrapped.classLoader());
    }
    queues = new HashMap<>();
    if (unwrapped.isDeployment()) {
      unwrapped.addCloseHook(this);
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

  public <T> void queue(Message<?> message, BiConsumer<String, Promise<T>> selectHandler, Promise<T> promise) {
    ctx.emit(v -> {
      String address = message.address();
      SerializerQueue queue = queues.computeIfAbsent(address, SerializerQueue::new);
      queue.add(message, selectHandler, promise);
    });
  }

  @Override
  public void close(Promise<Void> completion) {
    ctx.emit(v -> {
      for (SerializerQueue queue : queues.values()) {
        queue.close();
      }
      completion.complete();
    });
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

    <U> void add(Message<?> msg, BiConsumer<String, Promise<U>> selectHandler, Promise<U> promise) {
      SerializedTask<U> serializedTask = new SerializedTask<>(ctx, msg, selectHandler);
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
      final BiConsumer<String, Promise<U>> selectHandler;
      final Promise<U> internalPromise;

      SerializedTask(
        ContextInternal context,
        Message<?> msg,
        BiConsumer<String, Promise<U>> selectHandler) {
        this.msg = msg;
        this.selectHandler = selectHandler;
        this.internalPromise = context.promise();
      }

      void process() {
        selectHandler.accept(msg.address(), internalPromise);
      }

      @Override
      public void handle(AsyncResult<U> ar) {
        processed();
      }
    }
  }
}
