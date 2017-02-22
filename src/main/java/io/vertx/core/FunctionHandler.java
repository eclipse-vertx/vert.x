package io.vertx.core;

import io.vertx.codegen.annotations.VertxGen;
import java.util.Objects;

/**
 *  A generic FunctionHandler interface
 *  <p>
 *  This interface is copy of the JDK FunctionHandler interface to support VertxGen and
 *  composing {@link Handler}
 *  <p>
 *
 *  @author <a href="http://fzakaria.com">Farid Zakaria</a>
 */
@VertxGen
@FunctionalInterface
public interface FunctionHandler<E> {

    Handler<E> apply(Handler<E> var1);

    default <V> FunctionHandler<E> compose(FunctionHandler<E> var1) {
        Objects.requireNonNull(var1);
        return (var2) -> {
            return this.apply(var1.apply(var2));
        };
    }

    default <V> FunctionHandler<E> andThen(FunctionHandler<E> var1) {
        Objects.requireNonNull(var1);
        return (var2) -> {
            return var1.apply(this.apply(var2));
        };
    }

    static <E> FunctionHandler<E> identity() {
        return (var0) -> {
            return var0;
        };
    }
}
