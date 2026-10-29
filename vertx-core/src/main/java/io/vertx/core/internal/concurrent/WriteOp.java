package io.vertx.core.internal.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

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

  public boolean drainHandlerSet() {
    return handler(DRAIN_HANDLER_MASK, false);
  }

  public boolean disposeHandlerSet() {
    return handler(DISPOSE_HANDLER_MASK, true);
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

  public void write() {
  }

  public void cancel(Throwable cause) {
  }

  protected void handleDrain() {
  }

  protected void handleDispose() {
  }

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
