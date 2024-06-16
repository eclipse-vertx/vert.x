package io.vertx.tests.wrapper;

import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.http.HttpServerRequestWrapper;

/**
 * Just here to make sure that a class extending {@link HttpServerRequestWrapper} does not need to implement any method.
 *
 * We need this class to not override/implement method of the {@link HttpServerRequestWrapper} interface and compile.
 */
public class HttpServerRequestWrapperImpl extends HttpServerRequestWrapper {

  public HttpServerRequestWrapperImpl(HttpServerRequestInternal delegate) {
    super(delegate);
  }
}
