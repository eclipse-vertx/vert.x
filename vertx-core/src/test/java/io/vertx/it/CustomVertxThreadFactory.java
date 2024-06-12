package io.vertx.it;

import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;

import java.util.concurrent.TimeUnit;

public class CustomVertxThreadFactory implements VertxThreadFactory {

  @Override
  public VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    return new CustomVertxThread(target, name, worker, maxExecTime, maxExecTimeUnit);
  }
}
