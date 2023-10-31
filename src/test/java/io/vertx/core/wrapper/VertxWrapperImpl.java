package io.vertx.core.wrapper;

import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.VertxWrapper;

/**
 * Just here to make sure that a class extending {@link VertxWrapper} does not need to implement any method.
 *
 * We need this class to not override/implement method of the {@link VertxWrapper} interface and compile.
 */
public class VertxWrapperImpl extends VertxWrapper {

  protected VertxWrapperImpl(VertxInternal delegate) {
    super(delegate);
  }
}
