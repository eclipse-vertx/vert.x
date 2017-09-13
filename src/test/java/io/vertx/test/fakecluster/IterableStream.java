/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.fakecluster;

import io.vertx.core.Context;
import io.vertx.core.Handler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * @author Thomas Segismont
 */
public class IterableStream<T> implements io.vertx.core.streams.ReadStream<T> {

  private static final int BATCH_SIZE = 10;

  private final Context context;
  private final Iterable<T> iterable;

  private Iterator<T> iterator;
  private Deque<T> queue;
  private Handler<T> dataHandler;
  private Handler<Void> endHandler;
  private boolean paused;
  private boolean readInProgress;
  private boolean closed;

  public IterableStream(Context context, Iterable<T> iterable) {
    this.context = context;
    this.iterable = iterable;
  }

  @Override
  public synchronized IterableStream<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public synchronized IterableStream<T> handler(Handler<T> handler) {
    checkClosed();
    if (handler == null) {
      closed = true;
    } else {
      dataHandler = handler;
      if (!paused) {
        doRead();
      }
    }
    return this;
  }

  private void checkClosed() {
    if (closed) {
      throw new IllegalArgumentException("Stream is closed");
    }
  }

  @Override
  public synchronized IterableStream<T> pause() {
    checkClosed();
    paused = true;
    return this;
  }

  @Override
  public synchronized IterableStream<T> resume() {
    checkClosed();
    if (paused) {
      paused = false;
      if (dataHandler != null) {
        doRead();
      }
    }
    return this;
  }

  private synchronized void doRead() {
    if (readInProgress) {
      return;
    }
    readInProgress = true;
    if (iterator == null) {
      iterator = iterable.iterator();
    }
    if (queue == null) {
      queue = new ArrayDeque<>(BATCH_SIZE);
    }
    if (!queue.isEmpty()) {
      context.runOnContext(v -> emitQueued());
      return;
    }
    for (int i = 0; i < BATCH_SIZE && iterator.hasNext(); i++) {
      queue.add(iterator.next());
    }
    if (queue.isEmpty()) {
      context.runOnContext(v -> {
        synchronized (this) {
          readInProgress = false;
          closed = true;
          if (endHandler != null) {
            endHandler.handle(null);
          }
        }
      });
      return;
    }
    context.runOnContext(v -> emitQueued());
  }

  private synchronized void emitQueued() {
    while (!queue.isEmpty() && !paused && !closed) {
      dataHandler.handle(queue.remove());
    }
    readInProgress = false;
    if (!paused && !closed) {
      doRead();
    }
  }

  @Override
  public synchronized IterableStream<T> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
