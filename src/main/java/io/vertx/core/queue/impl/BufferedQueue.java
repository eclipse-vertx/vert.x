/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.queue.impl;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Arguments;
import io.vertx.core.queue.Queue;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BufferedQueue<T> implements Queue<T> {

  private ArrayDeque<T> pending = new ArrayDeque<>();
  private final long highWaterMark;
  private final int maxElementsPerTick;
  private long demand;
  private Handler<Void> writableHandler;
  private Handler<Void> emptyHandler;
  private Handler<T> elementHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean emitting;
  private boolean writable;
  private final Context context;

  public BufferedQueue(Context context, long highWaterMark, int maxElementsPerTick) {
    Arguments.require(highWaterMark >= 0, "highWaterMark " + highWaterMark + " >= 0");
    Arguments.require(maxElementsPerTick > 0, "maxElementsPerTick " + highWaterMark + " > 0");
    this.context = context;
    this.highWaterMark = highWaterMark;
    this.maxElementsPerTick = maxElementsPerTick;
    this.demand = Long.MAX_VALUE;
    this.writable = true;
    this.emitting = false;
  }

  @Override
  public boolean add(T element) {
    boolean r;
    synchronized (this) {
      if (emitting || demand == 0) {
        pending.add(element);
        writable = pending.size() < highWaterMark;
        return writable;
      } else {
        emitting = true;
        r = writable;
        if (pending.size() > 0) {
          pending.add(element);
          element = pending.poll();
        }
      }
    }
    safeHandle(element);
    return r;
  }

  /**
   * Calling this method assumes to own the emission and demand > 0, note the thread owning the emission can implicitly
   * transfer it to the context thread and should not assume ownership after this method is called.
   *
   * @param elt the element to offer
   */
  private void safeHandle(T elt) {
    if (context == null || context == Vertx.currentContext()) {
      Handler<T> handler;
      synchronized (this) {
        handler = elementHandler;
        if (demand != Long.MAX_VALUE) {
          demand--;
        }
      }
      if (handler != null) {
        handler.handle(elt);
      }
      boolean drain;
      synchronized (this) {
        drain = pending.size() > 0 && demand > 0L;
      }
      if (drain) {
        drain();
      } else {
        synchronized (this) {
          emitting = false;
        }
      }
    } else {
      context.runOnContext(v -> {
        safeHandle(elt);
      });
    }
  }

  @Override
  public boolean addAll(Iterable<T> iterable) {
    boolean ret;
    synchronized (this) {
      Iterator<T> it = iterable.iterator();
      if (!it.hasNext()) {
        return writable;
      }
      do {
        pending.add(it.next());
      } while (it.hasNext());
      writable = pending.size() < highWaterMark;
      if (emitting) {
        return writable;
      }
      emitting = true;
      ret = writable;
    }
    drain();
    return ret;
  }

  @Override
  public synchronized Queue<T> writableHandler(Handler<Void> handler) {
    writableHandler = handler;
    return this;
  }

  @Override
  public synchronized Queue<T> emptyHandler(Handler<Void> handler) {
    emptyHandler = handler;
    return this;
  }

  @Override
  public synchronized boolean isPaused() {
    return demand == 0;
  }

  @Override
  public synchronized Queue<T> handler(Handler<T> handler) {
    elementHandler = handler;
    return this;
  }

  @Override
  public synchronized Queue<T> exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized Queue<T> pause() {
    demand = 0;
    return this;
  }

  private void safeDrain() {
    if (context == null || context == Vertx.currentContext()) {
      drain();
    } else if (context != Vertx.currentContext()) {
      context.runOnContext(v -> drain());
    }
  }

  private void drain() {
    if (context == null) {
      for (;drainBatch();) {
        //
      }
      stopEmitting();
    } else {
      if (drainBatch()) {
        context.runOnContext(v -> drain());
      } else {
        stopEmitting();
      }
    }
  }

  private boolean drainBatch() {
    int max = maxElementsPerTick;
    while (max-- > 0) {
      Handler<T> handler;
      T event;
      synchronized (this) {
        if (demand == 0 || (event = pending.poll()) == null) {
          return false;
        }
        if (demand != Long.MAX_VALUE) {
          demand--;
        }
        handler = elementHandler;
      }
      if (handler != null) {
        try {
          handler.handle(event);
        } catch (Throwable e) {
          handleException(e);
        }
      }
    }
    return true;
  }

  private void stopEmitting() {
    Handler<Void> h1 = null;
    Handler<Void> h2 = null;
    synchronized (this) {
      emitting = false;
      if (!writable && pending.isEmpty()) {
        writable = true;
        h1 = writableHandler;
      }
      if (pending.isEmpty()) {
        h2 = emptyHandler;
      }
    }
    if (h1 != null) {
      h1.handle(null);
    }
    if (h2 != null) {
      h2.handle(null);
    }
  }

  @Override
  public Queue<T> resume() {
    synchronized (this) {
      if (demand == Long.MAX_VALUE) {
        return this;
      }
      demand = Long.MAX_VALUE;
      if (emitting || pending.isEmpty()) {
        return this;
      }
      emitting = true;
    }
    safeDrain();
    return this;
  }

  @Override
  public Queue<T> take(long amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Invalid negative amount: " + amount);
    }
    if (amount == 0) {
      return this;
    }
    synchronized (this) {
      demand += amount;
      if (demand < 0L) {
        demand = Long.MAX_VALUE;
      }
      if (emitting || pending.isEmpty()) {
        return this;
      } else {
        emitting = true;
      }
    }
    safeDrain();
    return this;
  }

  @Override
  public T poll() {
    T elt;
    synchronized (this) {
      elt = pending.pop();
      if (emitting) {
        return elt;
      }
      emitting = true;
    }
    stopEmitting();
    return elt;
  }

  @Override
  public Iterable<T> pollAll() {
    Iterable<T> all;
    synchronized (this) {
      if (pending.isEmpty()) {
        return Collections.emptyList();
      }
      all = pending;
      pending = new ArrayDeque<>();
      if (emitting) {
        return all;
      }
    }
    stopEmitting();
    return all;
  }

  @Override
  public synchronized void clear() {
    pending.clear();
  }

  @Override
  public synchronized boolean isWritable() {
    return writable;
  }

  @Override
  public synchronized int size() {
    return pending.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return pending.isEmpty();
  }

  private void handleException(Throwable err) {
    Handler<Throwable> handler;
    synchronized (this) {
      if ((handler = exceptionHandler) == null) {
        return;
      }
    }
    handler.handle(err);
  }
}
