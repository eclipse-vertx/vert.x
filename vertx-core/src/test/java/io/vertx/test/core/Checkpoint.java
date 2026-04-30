package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Completable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>A checkpoint is an exit condition of a {@link VertxRunner} managed test.</p>
 *
 * <p>Usually a @BeforeClass/@Before/@Test/@After/@AfterClass declares a set of checkpoint as method parameters.</p>
 *
 * <ul>
 *   <li>when all checkpoints are succeeded, the test passes</li>
 *   <li>when at least one checkpoint is failed, the test fails </li>
 * </ul>
 */
public final class Checkpoint implements Completable<Object> {

  static final Exception SUCCESS = new Exception();

  Throwable completion;
  final CountDownLatch latch = new CountDownLatch(1);

  @Override
  public void complete(Object result, Throwable failure) {
    synchronized (this) {
      if (completion != null) {
        return;
      }
      completion = failure == null ? SUCCESS : failure;
    }
    latch.countDown();
  }

  /**
   * Returns a {@link CountDownLatch} wrapping this checkpoint, when the latch reaches {@code zero}, the checkpoint
   * is succeeded.
   *
   * @param count the latch count
   * @return the latch
   */
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
    if (c == null) {
      PlatformDependent.throwException(new TimeoutException());
    } else if (c != SUCCESS) {
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
