package io.vertx.core;

@FunctionalInterface
public interface Deployment2 {

  Future<?> start();

  default Future<?> stop() {
    return Future.succeededFuture();
  }
}
