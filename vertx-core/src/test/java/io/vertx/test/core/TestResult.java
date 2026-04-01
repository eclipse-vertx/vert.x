package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Completable;

import java.time.Duration;

public class TestResult implements AutoCloseable {

  public static Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

  private Duration timeout = DEFAULT_TIMEOUT;
  private int pending = 0;
  private boolean closed = false;
  private Throwable failure = null;
  private boolean closing = false;

  public TestResult eval(Runnable statement) {
    try {
      statement.run();
    } catch (Throwable e) {
      fail(e);
    }
    return this;
  }

  public synchronized TestResult fail(Throwable f) {
    if (f == null) {
      throw new IllegalArgumentException();
    }
    if (!closed) {
      if (failure == null) {
        failure = f;
        notify();
      }
    }
    return this;
  }

  public synchronized TestResult timeout(Duration timeout) {
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException();
    }
    this.timeout = timeout;
    return this;
  }

  public synchronized Completable<Object> verifying() {
    if (closed) {
      throw new IllegalStateException();
    }
    pending++;
    return (res, err) -> {
      synchronized (TestResult.this) {
        if (closed) {
          return;
        }
        if (err != null && failure == null) {
          failure = err;
        } else if (--pending == 0) {
          TestResult.this.notify();
        }
      }
    };
  }

  @Override
  public void close() {
    synchronized (this) {
      if (!closed) {
        if (closing) {
          throw new IllegalStateException();
        }
        closing = true;
        if (pending > 0) {
          try {
            wait(timeout.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            PlatformDependent.throwException(e);
          }
        }
        closed = true;
        if (failure != null) {
          PlatformDependent.throwException(failure);
        }
      }
    }
  }
}
