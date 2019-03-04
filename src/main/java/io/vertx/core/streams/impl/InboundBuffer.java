/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.streams.impl;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxThread;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * A buffer that transfers elements to an handler with back-pressure.
 * <p/>
 * The buffer is softly bounded, i.e the producer can {@link #write} any number of elements and shall
 * cooperate with the buffer to not overload it.
 *
 * <h3>Writing to the buffer</h3>
 * When the producer writes an element to the buffer, the boolean value returned by the {@link #write} method indicates
 * whether it can continue safely adding more elements or stop.
 * <p/>
 * The producer can set a {@link #drainHandler} to be signaled when it can resume writing again. When a {@code write}
 * returns {@code false}, the drain handler will be called when the buffer becomes writable again. Note that subsequent
 * call to {@code write} will not prevent the drain handler to be called.
 *
 * <h3>Reading from the buffer</h3>
 * The consumer should set an {@link #handler} to consume elements.
 *
 * <h3>Buffer mode</h3>
 * The buffer is either in <i>flowing</i> or <i>fetch</i> mode.
 * <ul>
 *   <i>Initially the buffer is in <i>flowing</i> mode.</i>
 *   <li>When the buffer is in <i>flowing</i> mode, elements are delivered to the {@code handler}.</li>
 *   <li>When the buffer is in <i>fetch</i> mode, only the number of requested elements will be delivered to the {@code handler}.</li>
 * </ul>
 * The mode can be changed with the {@link #pause()}, {@link #resume()} and {@link #fetch} methods:
 * <ul>
 *   <li>Calling {@link #resume()} sets the <i>flowing</i> mode</li>
 *   <li>Calling {@link #pause()} sets the <i>fetch</i> mode and resets the demand to {@code 0}</li>
 *   <li>Calling {@link #fetch(long)} requests a specific amount of elements and adds it to the actual demand</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 *
 * To avoid data races, write methods must be called from the context thread associated with the buffer, when that's
 * not the case, an {@code IllegalStateException} is thrown.
 * <p/>
 * Other methods can be called from any thread.
 * <p/>
 * The handlers will always be called from a context thread.
 * <p/>
 * <strong>WARNING</strong>: this class is mostly useful for implementing the {@link io.vertx.core.streams.ReadStream}
 * and has little or no use within a regular application.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class InboundBuffer<E> {

  private final ContextInternal context;
  private final ArrayDeque<E> pending;
  private final long highWaterMark;
  private long demand;
  private Handler<E> handler;
  private boolean overflow;
  private Handler<Void> drainHandler;
  private Handler<Void> emptyHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean emitting;

  public InboundBuffer(Context context) {
    this(context, 16L);
  }

  public InboundBuffer(Context context, long highWaterMark) {
    Objects.requireNonNull(context, "context must not be null");
    Arguments.require(highWaterMark >= 0, "highWaterMark " + highWaterMark + " >= 0");
    this.context = (ContextInternal) context;
    this.highWaterMark = highWaterMark;
    this.demand = Long.MAX_VALUE;
    this.pending = new ArrayDeque<>();
  }

  private void checkThread() {
    Thread thread = Thread.currentThread();
    if (!(thread instanceof VertxThread)) {
      throw new IllegalStateException("This operation must be called from a Vert.x thread");
    }
  }

  /**
   * Write an {@code element} to the buffer. The element will be delivered synchronously to the handler when
   * it is possible, otherwise it will be queued for later delivery.
   *
   * @param element the element to add
   * @return {@code true} when the buffer is full and the producer should stop writing
   */
  public boolean write(E element) {
    checkThread();
    Handler<E> handler;
    synchronized (this) {
      if (demand == 0L || emitting) {
        pending.add(element);
        return checkWritable();
      } else {
        if (demand != Long.MAX_VALUE) {
          --demand;
        }
        emitting = true;
        handler = this.handler;
      }
    }
    handleEvent(handler, element);
    return emitPending();
  }

  private boolean checkWritable() {
    if (demand == Long.MAX_VALUE) {
      return true;
    } else {
      long actual = pending.size() - demand;
      boolean writable = actual < highWaterMark;
      overflow |= !writable;
      return writable;
    }
  }

  /**
   * Write an {@code iterable} of {@code elements}.
   *
   * @see #write(E)
   * @param elements the elements to add
   * @return {@code true} if the buffer is full
   */
  public boolean write(Iterable<E> elements) {
    checkThread();
    synchronized (this) {
      for (E element : elements) {
        pending.add(element);
      }
      if (demand == 0L || emitting) {
        return checkWritable();
      } else {
        emitting = true;
      }
    }
    return emitPending();
  }

  private boolean emitPending() {
    E element;
    Handler<E> h;
    while (true) {
      synchronized (this) {
        int size = pending.size();
        if (demand == 0L) {
          emitting = false;
          boolean writable = size < highWaterMark;
          overflow |= !writable;
          return writable;
        } else if (size == 0) {
          emitting = false;
          return true;
        }
        if (demand != Long.MAX_VALUE) {
          demand--;
        }
        element = pending.poll();
        h = this.handler;
      }
      handleEvent(h, element);
    }
  }

  /**
   * Drain the buffer.
   * <p/>
   * Calling this assumes {@code (demand > 0L && !pending.isEmpty()) == true}
   */
  private void drain() {
    int emitted = 0;
    Handler<Void> drainHandler;
    Handler<Void> emptyHandler;
    while (true) {
      E element;
      Handler<E> handler;
      synchronized (this) {
        int size = pending.size();
        if (size == 0) {
          emitting = false;
          if (overflow) {
            overflow = false;
            drainHandler = this.drainHandler;
          } else {
            drainHandler = null;
          }
          emptyHandler = emitted > 0 ? this.emptyHandler : null;
          break;
        } else if (demand == 0L) {
          emitting = false;
          return;
        }
        emitted++;
        if (demand != Long.MAX_VALUE) {
          demand--;
        }
        element = pending.poll();
        handler = this.handler;
      }
      handleEvent(handler, element);
    }
    if (drainHandler != null) {
      handleEvent(drainHandler, null);
    }
    if (emptyHandler != null) {
      handleEvent(emptyHandler, null);
    }
  }

  private <T> void handleEvent(Handler<T> handler, T element) {
    if (handler != null) {
      try {
        handler.handle(element);
      } catch (Throwable t) {
        handleException(t);
      }
    }
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

  /**
   * Request a specific {@code amount} of elements to be fetched, the amount is added to the actual demand.
   * <p/>
   * Pending elements in the buffer will be delivered asynchronously on the context to the handler.
   * <p/>
   * This method can be called from any thread.
   *
   * @return {@code true} when the buffer will be drained
   */
  public boolean fetch(long amount) {
    if (amount < 0L) {
      throw new IllegalArgumentException();
    }
    synchronized (this) {
      demand += amount;
      if (demand < 0L) {
        demand = Long.MAX_VALUE;
      }
      if (emitting || (pending.isEmpty() && !overflow)) {
        return false;
      }
      emitting = true;
    }
    context.runOnContext(v -> drain());
    return true;
  }

  /**
   * Read the most recent element synchronously.
   * <p/>
   * No handler will be called.
   *
   * @return the most recent element or {@code null} if no element was in the buffer
   */
  public E read() {
    synchronized (this) {
      return pending.poll();
    }
  }

  /**
   * Clear the buffer synchronously.
   * <p/>
   * No handler will be called.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public synchronized InboundBuffer<E> clear() {
    pending.clear();
    return this;
  }

  /**
   * Pause the buffer, it sets the buffer in {@code fetch} mode and clears the actual demand.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public synchronized InboundBuffer<E> pause() {
    demand = 0L;
    return this;
  }

  /**
   * Resume the buffer, and sets the buffer in {@code flowing} mode.
   * <p/>
   * Pending elements in the buffer will be delivered asynchronously on the context to the handler.
   * <p/>
   * This method can be called from any thread.
   *
   * @return {@code true} when the buffer will be drained
   */
  public boolean resume() {
    return fetch(Long.MAX_VALUE);
  }

  /**
   * Set an {@code handler} to be called with elements available from this buffer.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  public synchronized InboundBuffer<E> handler(Handler<E> handler) {
    this.handler = handler;
    return this;
  }

  /**
   * Set an {@code handler} to be called when the buffer is drained and the producer can resume writing to the buffer.
   *
   * @param handler the handler to be called
   * @return a reference to this, so the API can be used fluently
   */
  public synchronized InboundBuffer<E> drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  /**
   * Set an {@code handler} to be called when the buffer becomes empty.
   *
   * @param handler the handler to be called
   * @return a reference to this, so the API can be used fluently
   */
  public synchronized InboundBuffer<E> emptyHandler(Handler<Void> handler) {
    emptyHandler = handler;
    return this;
  }

  /**
   * Set an {@code handler} to be called when an exception is thrown by an handler.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  public synchronized InboundBuffer<E> exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  /**
   * @return whether the buffer is empty
   */
  public synchronized boolean isEmpty() {
    return pending.isEmpty();
  }

  /**
   * @return whether the buffer is writable
   */
  public synchronized boolean isWritable() {
    return pending.size() < highWaterMark;
  }

  /**
   * @return whether the buffer is paused, i.e it is in {@code fetch} mode and the demand is {@code 0}.
   */
  public synchronized boolean isPaused() {
    return demand == 0L;
  }

  /**
   * @return the actual number of elements in the buffer
   */
  public synchronized int size() {
    return pending.size();
  }
}
