package io.vertx.it;

import io.vertx.core.spi.VertxFactory;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.internal.core.spi.context.ContextLocal;

public class CustomContextLocal implements VertxServiceProvider  {

  public static ContextLocal<Object> CUSTOM_LOCAL = ContextLocal.registerLocal(Object.class);
  public static volatile boolean initialized;

  @Override
  public void init(VertxFactory builder) {
    initialized = true;
  }
}
