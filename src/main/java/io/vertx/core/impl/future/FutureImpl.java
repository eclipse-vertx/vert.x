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

package io.vertx.core.impl.future;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceThrowable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe / lock-free implementation of a {@link Future} relying on compare-and-swap, busy-waiting
 * and piggybacking.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @param <T> The result type of the future.
 */
class FutureImpl<T> extends FutureBase<T> {

  /**
   * All possible states of the future, the initial state is <i>NEW</i> then the possible state transitions are:
   * <p>
   * <ul>
   *   <li><i>NEW</i> -> <i>COMPLETING</i> -> <i>SUCCESS</i></li>
   *   <li><i>NEW</i> -> <i>COMPLETING</i> -> <i>FAILURE</i></li>
   *   <li><i>NEW</i> -> <i>REGISTERING</i> -> <i>REGISTERED</i> -> <i>COMPLETING</i> -> <i>SUCCESS</i></li>
   *   <li><i>NEW</i> -> <i>REGISTERING</i> -> <i>REGISTERED</i> -> <i>COMPLETING</i> -> <i>FAILURE</i></li>
   * </ul>
   *
   * <p>
   * The description of all possible states:
   * <table>
   *   <col width="25%"/>
   *   <col width="75%"/>
   *   <thead>
   *     <tr><th>State</th><th>Description</th></tr>
   *   <thead>
   *   <tbody>
   *      <tr><td>{@link FutureImpl#NEW}</td><td>The initial state</td></tr>
   *      <tr><td>{@link FutureImpl#REGISTERED}</td><td>The state in case at least one listener has been registered</td></tr>
   *      <tr><td>{@link FutureImpl#COMPLETING}</td><td>The state in case the result of the future has been received</td></tr>
   *      <tr><td>{@link FutureImpl#REGISTERING}</td><td>The state in case a listener is currently being registered</td></tr>
   *      <tr><td>{@link FutureImpl#FAILURE}</td><td>The state in case the future failed</td></tr>
   *      <tr><td>{@link FutureImpl#SUCCESS}</td><td>The state in case the future could be executed with success</td></tr>
   *   </tbody>
   * </table>
   */
  private static final int NEW         = 0;
  private static final int REGISTERED  = 1;
  private static final int COMPLETING  = 2;
  private static final int REGISTERING = 3;
  private static final int FAILURE     = 4;
  private static final int SUCCESS     = 5;
  /**
   * The resulting value of the future. It will be of type T in case of a success and of type {@code Throwable}
   * in case of a failure.
   */
  private Object value;
  /**
   * The listener to call once the result of the future will be received.
   */
  private Listener<T> listener;
  /**
   * The current state of the future.
   */
  private final AtomicInteger state = new AtomicInteger(NEW);

  /**
   * Create a future that hasn't completed yet
   */
  FutureImpl() {
    super();
  }

  /**
   * Create a future that hasn't completed yet
   */
  FutureImpl(ContextInternal context) {
    super(context);
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  public T result() {
    if (this.state.get() == SUCCESS) {
      return (T) value;
    }
    return null;
  }

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public Throwable cause() {
    if (this.state.get() == FAILURE) {
      return (Throwable) value;
    }
    return null;
  }

  /**
   * Did it succeed?
   */
  public boolean succeeded() {
    return state.get() == SUCCESS;
  }

  /**
   * Did it fail?
   */
  public boolean failed() {
    return state.get() == FAILURE;
  }

  /**
   * Has it completed?
   */
  public boolean isComplete() {
    return isComplete(this.state.get());
  }

  @Override
  public Future<T> onSuccess(Handler<T> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    addListener(new Listener<T>() {
      @Override
      public void onSuccess(T value) {
        handler.handle(value);
      }
      @Override
      public void onFailure(Throwable failure) {
      }
    });
    return this;
  }

  @Override
  public Future<T> onFailure(Handler<Throwable> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    addListener(new Listener<T>() {
      @Override
      public void onSuccess(T value) {
      }
      @Override
      public void onFailure(Throwable failure) {
        handler.handle(failure);
      }
    });
    return this;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    Listener<T> listener;
    if (handler instanceof Listener) {
      listener = (Listener<T>) handler;
    } else {
      listener = new Listener<T>() {
        @Override
        public void onSuccess(T value) {
          handler.handle(FutureImpl.this);
        }
        @Override
        public void onFailure(Throwable failure) {
          handler.handle(FutureImpl.this);
        }
      };
    }
    addListener(listener);
    return this;
  }

  @Override
  public void addListener(Listener<T> listener) {
    for (;;) {
      int s = this.state.get();
      if (isComplete(s)) {
        if (value instanceof Throwable) {
          emitFailure((Throwable) value, listener);
        } else {
          emitSuccess((T) value, listener);
        }
        return;
      } else if (s <= REGISTERED && this.state.compareAndSet(s, REGISTERING)) {
        register(listener);
        this.state.set(REGISTERED);
        return;
      }
    }
  }

  public boolean tryComplete(T result) {
    for (;;) {
      int s = this.state.get();
      if (isComplete(s)) {
        return false;
      } else if (s <= REGISTERED && this.state.compareAndSet(s, COMPLETING)) {
        Listener<T> l = listener;
        this.listener = null;
        this.value = result;
        this.state.set(SUCCESS);
        if (l != null) {
          emitSuccess(result, l);
        }
        return true;
      }
    }
  }

  public boolean tryFail(Throwable cause) {
    for (;;) {
      int s = this.state.get();
      if (isComplete(s)) {
        return false;
      } else if (s <= REGISTERED && this.state.compareAndSet(s, COMPLETING)) {
        Listener<T> l = listener;
        this.listener = null;
        this.value = cause == null ? new NoStackTraceThrowable(null) : cause;
        this.state.set(FAILURE);
        if (l != null) {
          emitFailure(cause, l);
        }
        return true;
      }
    }
  }

  @Override
  public String toString() {
    int s = this.state.get();
    if (s == SUCCESS) {
      return String.format("Future{result=%s}", value);
    } else if (s == FAILURE) {
      return String.format("Future{cause=%s}", ((Throwable) value).getMessage());
    }
    return "Future{unresolved}";
  }

  /**
   * Indicates whether the given state is either a success or a failure.
   * @param state the state to test.
   * @return {@code true} if the state is either a success or a failure, {@code false} otherwise.
   */
  private boolean isComplete(int state) {
    return state >= FAILURE;
  }

  /**
   * Registers the given listener.
   * @param listener the listener to register.
   */
  private void register(Listener<T> listener) {
    if (this.listener == null) {
      this.listener = listener;
    } else {
      ListenerArray<T> listeners;
      if (this.listener instanceof FutureImpl.ListenerArray) {
        listeners = (ListenerArray<T>) this.listener;
      } else {
        listeners = new ListenerArray<>();
        listeners.add(this.listener);
        this.listener = listeners;
      }
      listeners.add(listener);
    }
  }

  /**
   * A type of collection of {@code Listener<T>} that will be used if several listeners are registered.
   * @param <T> the result type of the future.
   */
  private static class ListenerArray<T> extends ArrayList<Listener<T>> implements Listener<T> {
    @Override
    public void onSuccess(T value) {
      for (Listener<T> handler : this) {
        handler.onSuccess(value);
      }
    }
    @Override
    public void onFailure(Throwable failure) {
      for (Listener<T> handler : this) {
        handler.onFailure(failure);
      }
    }
  }
}
