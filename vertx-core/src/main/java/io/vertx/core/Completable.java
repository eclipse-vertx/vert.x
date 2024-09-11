package io.vertx.core;

@FunctionalInterface
public interface Completable<T> {

  default void succeed() {
    complete(null, null);
  }

  default void succeed(T result) {
    complete(result, null);
  }

  default void fail(Throwable err) {
    complete(null, err);
  }

  void complete(T result, Throwable err);

}
