package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Completable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Checkpoint implements Completable<Void> {

  static final Exception SUCCESS = new Exception();

  Throwable completion;
  final CountDownLatch latch = new CountDownLatch(1);

  @Override
  public void complete(Void result, Throwable failure) {
    synchronized (this) {
      if (completion != null) {
        return;
      }
      completion = failure == null ? SUCCESS : failure;
    }
    latch.countDown();
  }

  public CountDownLatch asLatch(int count) {
    return new CountingCheckpoint(this, count);
  }

  public void await() {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      PlatformDependent.throwException(e);
    }
  }

  public void awaitSuccess() {
    await();
    Throwable c = completion;
    if (c != SUCCESS) {
      PlatformDependent.throwException(c);
    }
  }

  public void awaitFailure() {
    await();
    Throwable c = completion;
    if (c == SUCCESS) {
      throw new AssertionError("Unexpected success");
    }
  }
}
