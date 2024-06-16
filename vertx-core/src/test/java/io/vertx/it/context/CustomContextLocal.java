package io.vertx.it.context;

import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;

public class CustomContextLocal implements VertxServiceProvider  {

  public static ContextLocal<Object> CUSTOM_LOCAL = ContextLocal.registerLocal(Object.class);
  public static volatile boolean initialized;

  @Override
  public void init(VertxBootstrap builder) {
    initialized = true;
  }
}
