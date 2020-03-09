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
package io.vertx.test.fakestream;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.Arguments;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

/**
 * A bi-directional stream for testing purpose.
 * <p/>
 * The stream is thread safe and synchronous.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeStream<T> implements ReadStream<T>, WriteStream<T> {

  private boolean emitting;
  private long highWaterMark = 16L;
  private Handler<Throwable> exceptionHandler;
  private Handler<T> itemHandler;
  private Handler<Void> endHandler;
  private final Deque<Object> pending = new ArrayDeque<>();
  private long demand = Long.MAX_VALUE;
  private boolean ended;
  private Future<Void> end = Future.succeededFuture();
  private boolean overflow;
  private Handler<Void> drainHandler;
  private int pauseCount;
  private int resumeCount;

  public synchronized int pauseCount() {
    return pauseCount;
  }

  public synchronized int resumeCount() {
    return resumeCount;
  }

  public synchronized boolean isPaused() {
    return demand == 0L;
  }

  public synchronized boolean isEnded() {
    return ended;
  }

  public synchronized long demand() {
    return demand;
  }

  @Override
  public synchronized FakeStream<T> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  public synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  @SafeVarargs
  public final boolean emit(T... elements) {
    return emit(Stream.of(elements));
  }

  public synchronized boolean emit(Stream<T> stream) {
    if (ended) {
      throw new IllegalStateException();
    }
    stream.forEach(pending::add);
    checkPending();
    boolean writable = pending.size() <= highWaterMark;
    overflow |= !writable;
    return writable;
  }

  public Future<Void> end() {
    Promise<Void> promise = Promise.promise();
    end(promise);
    return promise.future();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> h) {
    synchronized(this) {
      if (ended) {
        throw new IllegalStateException();
      }
      ended = true;
      Promise<Void> promise = Promise.promise();
      promise.future().onComplete(ar -> {
        if (h != null) {
          h.handle(ar);
        }
        Handler<Void> handler = endHandler();
        if (handler != null) {
          handler.handle(null);
        }
      });
      pending.add(promise);
    }
    checkPending();
  }

  public synchronized void fail(Throwable err) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      exceptionHandler.handle(err);
    }
  }

  @Override
  public synchronized FakeStream<T> handler(Handler<T> handler) {
    this.itemHandler = handler;
    return this;
  }

  public synchronized Handler<T> handler() {
    return itemHandler;
  }

  @Override
  public synchronized FakeStream<T> pause() {
    pauseCount++;
    demand = 0L;
    return this;
  }

  private void checkPending() {
    if (emitting) {
      return;
    }
    emitting = true;
    Object elt;
    while (demand > 0L && (elt = pending.poll()) != null) {
      if (demand != Long.MAX_VALUE) {
        demand--;
      }
      if (elt instanceof Promise) {
        end.onComplete((Promise) elt);
      } else {
        Handler<T> handler = itemHandler;
        if (handler != null) {
          handler.handle((T) elt);
        }
      }
    }
    if (pending.isEmpty() && overflow) {
      overflow = false;
      Handler<Void> handler = drainHandler;
      drainHandler = null;
      if (handler != null) {
        handler.handle(null);
      }
    }
    emitting = false;
  }

  @Override
  public synchronized FakeStream<T> fetch(long amount) {
    Arguments.require(amount > 0L, "Fetch amount must be > 0L");
    demand += amount;
    if (demand < 0L) {
      demand = Long.MAX_VALUE;
    }
    checkPending();
    return this;
  }

  @Override
  public FakeStream<T> resume() {
    synchronized (this) {
      resumeCount++;
    }
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public synchronized FakeStream<T> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  public synchronized Handler<Void> endHandler() {
    return endHandler;
  }

  @Override
  public Future<Void> write(T data) {
    Future<Void> fut = Future.failedFuture("Not yet implemented");
    emit(data);
    return fut;
  }

  @Override
  public void write(T data, Handler<AsyncResult<Void>> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized FakeStream<T> setWriteQueueMaxSize(int maxSize) {
    highWaterMark = maxSize;
    return this;
  }

  @Override
  public synchronized boolean writeQueueFull() {
    return pending.size() > highWaterMark;
  }

  @Override
  public synchronized FakeStream<T> drainHandler(@Nullable Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  public synchronized Handler<Void> drainHandler() {
    return drainHandler;
  }

  public synchronized FakeStream<T> setEnd(Future<Void> fut) {
    if (ended) {
      throw new IllegalStateException();
    }
    end = fut;
    return this;
  }
}
