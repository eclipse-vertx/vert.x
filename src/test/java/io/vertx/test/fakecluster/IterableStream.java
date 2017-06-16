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
import io.vertx.core.streams.ReadStream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Thomas Segismont
 */
public class IterableStream<T> implements ReadStream<T> {

  private static final int BATCH_SIZE = 10;

  private final Context context;
  private final Iterable<T> iterable;

  private Iterator<T> iterator;
  private Handler<T> dataHandler;
  private Handler<Void> endHandler;
  private boolean paused;
  private boolean readInProgress;

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
    this.dataHandler = handler;
    if (dataHandler != null && !paused) {
      doRead();
    }
    return this;
  }

  @Override
  public synchronized IterableStream<T> pause() {
    paused = true;
    return this;
  }

  @Override
  public synchronized IterableStream<T> resume() {
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
    List<T> values = new ArrayList<>(BATCH_SIZE);
    for (int i = 0; i < BATCH_SIZE && iterator.hasNext(); i++) {
      values.add(iterator.next());
    }
    if (values.isEmpty()) {
      readInProgress = false;
      Handler<Void> endHandler = this.endHandler;
      if (endHandler != null) {
        context.runOnContext(v -> {
          endHandler.handle(null);
        });
      }
    } else {
      Handler<T> dataHandler = this.dataHandler;
      if (dataHandler != null) {
        context.runOnContext(v -> {
          values.forEach(dataHandler::handle);
          synchronized (this) {
            readInProgress = false;
          }
          context.runOnContext(v2 -> {
            synchronized (this) {
              if (this.dataHandler != null && !paused) {
                doRead();
              }
            }
          });
        });
      }
    }
  }

  @Override
  public synchronized IterableStream<T> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
