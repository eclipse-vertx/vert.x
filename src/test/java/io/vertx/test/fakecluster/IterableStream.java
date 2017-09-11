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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.shareddata.AsyncMapStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * @author Thomas Segismont
 */
public class IterableStream<T> implements AsyncMapStream<T> {

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
    this.dataHandler = handler;
    if (dataHandler != null && !paused) {
      doRead();
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
    while (!queue.isEmpty() && dataHandler != null && !paused && !closed) {
      dataHandler.handle(queue.remove());
    }
    readInProgress = false;
    if (dataHandler != null && !paused && !closed) {
      doRead();
    }
  }

  @Override
  public synchronized IterableStream<T> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    context.runOnContext(v -> {
      synchronized (this) {
        closed = true;
        if (completionHandler != null) {
          completionHandler.handle(Future.succeededFuture());
        }
      }
    });
  }
}
