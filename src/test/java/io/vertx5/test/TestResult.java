package io.vertx5.test;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import junit.framework.AssertionFailedError;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestResult {

  private final CompletableFuture<Void> result = new CompletableFuture<>();

  public void complete() {
    result.complete(null);
  }

  public TestResult run(Runnable r) {
    try {
      r.run();
    } catch (Throwable t) {
      result.completeExceptionally(t);
    }
    return this;
  }

  public <T> void assertSuccess(Future<T> future, Handler<T> cont) {
    future.onComplete(ar -> {
      if (ar.succeeded()) {
        run(() -> {
          cont.handle(ar.result());
        });
      } else {
        result.completeExceptionally(ar.cause());
      }
    });
  }

  public void await() {
    try {
      result.get(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionFailedError("Interrupted");
    } catch (ExecutionException e) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e.getCause());
      throw afe;
    } catch (TimeoutException e) {
      throw new AssertionFailedError("Timed out");
    }
  }

}
