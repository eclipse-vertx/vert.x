package io.vertx.it;

import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.ContextKey;

public class CustomContextKey implements VertxServiceProvider  {

  public static ContextKey<Object> CUSTOM_KEY = ContextKey.registerKey(Object.class);
  public static volatile boolean initialized;

  @Override
  public void init(VertxBuilder builder) {
    initialized = true;
  }
}
