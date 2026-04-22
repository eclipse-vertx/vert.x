package io.vertx.test.core;

import io.vertx.core.Vertx;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface VertxProvider extends Callable<Vertx> {

  default void close(Vertx vertx, Duration timeout) throws Exception {
    vertx
      .close()
      .await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }
}
