/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.streams;

import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.impl.Utils;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.core.streams.ReadStream;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adapts a Vert.x read stream to a blocking iterator, the main use case is dispatching a Vert.x read stream
 * to a virtual thread consumer.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ReadStreamIterator<E> implements Iterator<E>, Handler<E> {

  private static final Throwable END_SENTINEL = new VertxException("", true);

  public static <E> Iterator<E> iterator(ReadStream<E> stream) {
    ReadStreamIterator<E> iterator = new ReadStreamIterator<>(stream);
    iterator.init();
    return iterator;
  }

  private final ReadStream<E> stream;
  private final Queue<Object> queue;
  private final Lock lock;
  private final Condition consumerProgress;
  private Throwable ended;

  public ReadStreamIterator(ReadStream<E> stream) {
    this.stream = stream;
    this.queue = new ArrayDeque<>();
    this.lock = new ReentrantLock();
    this.consumerProgress = lock.newCondition();
  }

  /**
   * Signal the consumer that a resume operation is required to fill the buffer again.
   */
  static class Resume {
    final Object elt;
    public Resume(Object elt) {
      this.elt = elt;
    }
  }

  void init() {
    stream.handler(this);
    stream.exceptionHandler(this::handleEnd);
    stream.endHandler(v -> {
      handleEnd(END_SENTINEL);
    });
  }

  public void handle(E elt) {
    int size;
    boolean pause;
    lock.lock();
    try {
      size = queue.size();
      pause = size == 15;
      if (pause) {
        stream.pause();
        queue.add(new Resume(elt));
      } else {
        queue.add(elt);
      }
      consumerProgress.signal();
    } finally {
      lock.unlock();
    }
  }

  private void handleEnd(Throwable cause) {
    try {
      stream.endHandler(null);
      stream.exceptionHandler(null);
      stream.handler(null);
    } catch (Throwable ignore) {
    }
    lock.lock();
    try {
      ended = cause;
      consumerProgress.signalAll();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean hasNext() {
    lock.lock();
    try {
      while (true) {
        if (!queue.isEmpty()) {
          return true;
        }
        if (ended != null) {
          return false;
        }
        awaitProgress();
      }
    } finally {
      lock.unlock();
    }
  }

  private void awaitProgress() {
    io.vertx.core.impl.WorkerExecutor executor = io.vertx.core.impl.WorkerExecutor.unwrapWorkerExecutor();
    if (executor != null) {
      WorkerExecutor.Execution execution = executor.currentExecution();
      CountDownLatch latch = execution.trySuspend();
      try {
        consumerProgress.await();
        execution.resume();
        latch.await();
      } catch (InterruptedException e) {
        Utils.throwAsUnchecked(e);
      }
    } else {
      try {
        consumerProgress.await();
      } catch (InterruptedException e) {
        Utils.throwAsUnchecked(e);
      }
    }
  }

  @Override
  public E next() {
    Object elt;
    lock.lock();
    try {
      while (true) {
        elt = queue.poll();
        if (elt != null) {
          break;
        }
        Throwable t = ended;
        if (t != null) {
          if (t == END_SENTINEL) {
            throw new NoSuchElementException();
          } else {
            Utils.throwAsUnchecked(t);
          }
        }
        awaitProgress();
      }
    } finally {
      lock.unlock();
    }
    if (elt instanceof Resume) {
      elt = ((Resume)elt).elt;
      stream.resume();
    }
    return (E) elt;
  }
}
