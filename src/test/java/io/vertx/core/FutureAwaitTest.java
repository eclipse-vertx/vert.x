package io.vertx.core;

import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureAwaitTest extends VertxTestBase {

  @Test
  public void testAwaitFromEventLoopThread() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ctx.nettyEventLoop().execute(() -> {
      try {
        Future.await(future);
      } catch (IllegalStateException expected) {
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testAwaitFromNonVertxThread() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    Thread current = Thread.currentThread();
    new Thread(() -> {
      while (current.getState() != Thread.State.WAITING) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException ignore) {
        }
      }
      promise.complete("the-result");
    }).start();
    String res = Future.await(future);
    assertEquals("the-result", res);
  }

  @Test
  public void testAwaitWithTimeout() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    long now = System.currentTimeMillis();
    try {
      Future.await(future, 100, TimeUnit.MILLISECONDS);
      fail();
    } catch (TimeoutException expected) {
    }
    assertTrue((System.currentTimeMillis() - now) >= 100);
  }
}
