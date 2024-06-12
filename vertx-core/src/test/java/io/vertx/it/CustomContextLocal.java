package io.vertx.it;

import io.vertx.core.spi.VertxFactory;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;

public class CustomContextLocal implements VertxServiceProvider  {

  public static ContextLocal<Object> CUSTOM_LOCAL = ContextLocal.registerLocal(Object.class);
  public static volatile boolean initialized;

  @Override
  public void init(VertxFactory builder) {
    initialized = true;
  }
}
