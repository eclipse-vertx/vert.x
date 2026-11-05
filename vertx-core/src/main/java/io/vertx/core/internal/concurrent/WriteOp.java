/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The element of an {@link OutboundWriteQueue}: a write operation stamped at add time with the queue depth
 * and add order, carrying a one-shot signal — drained or disposed, exactly once — owned by a single atomic
 * status word.
 *
 * <p>Handler registration protocol: the registering side stores its handler <em>before</em> calling
 * {@code setXxxHandler()}, so the bit-publishing CAS safely publishes the handler to the signaling thread.
 * A matching signal recorded before the registration dispatches synchronously inside the set call; a
 * mismatched one means the handler will never be called and the set returns {@code true} so the caller can
 * clear its stored handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class WriteOp {

  public static int seqOf(WriteOp op) {
    return op.seq;
  }

  public static int depthOf(WriteOp op) {
    return op.depth;
  }

  private static final int DRAIN_HANDLER_MASK = 0x1;
  private static final int DISPOSE_HANDLER_MASK = 0x2;
  private static final int SIGNALED_MASK = 0x4;
  private static final int CLOSED_MASK = 0x8;
  private static final int FIRED_MASK = 0x10;

  int depth;
  int seq;
  boolean writable;
  private final AtomicInteger status = new AtomicInteger();

  private void signal(boolean writable) {
    int handlerBit = writable ? DRAIN_HANDLER_MASK : DISPOSE_HANDLER_MASK;
    while (true) {
      int st = status.get();
      if ((st & SIGNALED_MASK) != 0) {
        return;
      }
      int next = st | SIGNALED_MASK | (writable ? 0 : CLOSED_MASK);
      boolean fire = (st & handlerBit) != 0;
      if (fire) {
        next |= FIRED_MASK;
      }
      if (status.compareAndSet(st, next)) {
        if (fire) {
          if (writable) {
            handleDrain();
          } else {
            handleDispose();
          }
        }
        return;
      }
    }
  }

  /**
   * Claim the drain handler registration, the handler must be stored before calling this method.
   *
   * @return {@code true} when the handler will never be called and can be cleared, {@code false} when the
   *         registration is claimed — a drained signal recorded before the call has dispatched synchronously
   * @throws IllegalStateException when a drain handler is already set or the op was never enqueued
   */
  public boolean trySetDrainHandler() {
    return handler(DRAIN_HANDLER_MASK, false);
  }

  /**
   * Claim the dispose handler registration, the handler must be stored before calling this method.
   *
   * @return {@code true} when the handler will never be called and can be cleared, {@code false} when the
   *         registration is claimed — a disposed signal recorded before the call has dispatched synchronously
   * @throws IllegalStateException when a dispose handler is already set or the op was never enqueued
   */
  public boolean trySetDisposeHandler() {
    return handler(DISPOSE_HANDLER_MASK, true);
  }

  /**
   * Unset the drain handler registration.
   *
   * @return {@code true} when the registration was unset (or never set) and the handler can be cleared,
   *         {@code false} when the signal won the race and already captured the handler
   */
  public boolean tryUnsetDrainHandler() {
    return unsetHandler(DRAIN_HANDLER_MASK);
  }

  /**
   * Unset the dispose handler registration.
   *
   * @return {@code true} when the registration was unset (or never set) and the handler can be cleared,
   *         {@code false} when the signal won the race and already captured the handler
   */
  public boolean tryUnsetDisposeHandler() {
    return unsetHandler(DISPOSE_HANDLER_MASK);
  }

  private boolean unsetHandler(int handlerBit) {
    while (true) {
      int st = status.get();
      if ((st & handlerBit) == 0) {
        return true;
      }
      if ((st & SIGNALED_MASK) != 0) {
        // The signal won the race: the dispatch already captured the handler
        return false;
      }
      if (status.compareAndSet(st, st & ~handlerBit)) {
        return true;
      }
    }
  }

  private boolean handler(int handlerBit, boolean closed) {
    if (depth == 0 && (status.get() & CLOSED_MASK) == 0) {
      throw new IllegalStateException("Write was not enqueued through a depth-aware queue");
    }
    while (true) {
      int st = status.get();
      if ((st & handlerBit) != 0) {
        throw new IllegalStateException();
      }
      if ((st & SIGNALED_MASK) != 0) {
        if (((st & CLOSED_MASK) != 0) != closed) {
          // The signal does not match this handler: never called
          return true;
        } else {
          if ((st & FIRED_MASK) != 0) {
            throw new IllegalStateException();
          }
          if (status.compareAndSet(st, st | handlerBit | FIRED_MASK)) {
            if (handlerBit == DRAIN_HANDLER_MASK) {
              handleDrain();
            } else {
              handleDispose();
            }
            return false;
          }
        }
      } else {
        if (status.compareAndSet(st, st | handlerBit)) {
          return false;
        }
      }
    }
  }

  final void onDrain() {
    signal(true);
  }

  final void onDispose() {
    signal(false);
  }

  /**
   * Perform the write operation, called back by the queue when the op is consumed.
   */
  public void write() {
  }

  /**
   * Cancel the write, called back by the queue when the op is released unwritten or its write threw.
   *
   * @param cause the cancellation cause
   */
  public void cancel(Throwable cause) {
  }

  /**
   * Dispatch the drained signal to the registered handler.
   */
  protected void handleDrain() {
  }

  /**
   * Dispatch the disposed signal to the registered handler.
   */
  protected void handleDispose() {
  }

  /**
   * @return the writable status of the stream observed when this op was added, {@code false} once disposed
   * @throws IllegalStateException when the op was never enqueued
   */
  public final boolean isWritable() {
    if ((status.get() & CLOSED_MASK) != 0) {
      return false;
    } else {
      if (depth == 0) {
        throw new IllegalStateException("Write was not enqueued through a depth-aware queue");
      }
      return writable;
    }
  }
}
