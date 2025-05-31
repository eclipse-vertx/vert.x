package io.vertx.core;

@FunctionalInterface
public interface TriFunction<U, V, W, T> {

  T apply(U u, V v, W w);

}
