package io.vertx.it;

import io.vertx.core.impl.VertxThreadImpl;

import java.util.concurrent.TimeUnit;

public class CustomVertxThread extends VertxThreadImpl  {
  public CustomVertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    super(target, name, worker, maxExecTime, maxExecTimeUnit);
  }
}
