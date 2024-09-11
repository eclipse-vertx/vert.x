package io.vertx.core;

@FunctionalInterface
public interface Deployment1 {

  void start(Completable<Object> completable);

  default void stop(Completable<Object> completable) {
    completable.succeed(null);
  }
}
