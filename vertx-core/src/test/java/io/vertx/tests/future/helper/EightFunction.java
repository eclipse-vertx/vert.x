package io.vertx.tests.future.helper;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface EightFunction<A, B, C, D, E, F, G, H, R> {

  R apply(A a, B b, C c, D d, E e, F f, G g, H h);

  default <V> EightFunction<A, B, C, D, E, F, G, H, V> andThen(
    Function<? super R, ? extends V> after) {
    Objects.requireNonNull(after);
    return (a, b, c, d, e, f, g, h) -> after.apply(apply(a, b, c, d, e, f, g, h));
  }
}

