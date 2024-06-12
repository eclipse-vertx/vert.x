package io.vertx.core.impl;

import io.vertx.core.Context;
import io.vertx.core.spi.ContextProvider;

public class ContextProviderImpl implements ContextProvider {

  @Override
  public Context current() {
    return ContextInternal.current();
  }

  @Override
  public boolean isOnWorkerThread() {
    Thread t = Thread.currentThread();
    return t instanceof VertxThread && ((VertxThread) t).isWorker();
  }

  @Override
  public boolean isOnEventLoopThread() {
    Thread t = Thread.currentThread();
    return t instanceof VertxThread && !((VertxThread) t).isWorker();
  }

  @Override
  public boolean isOnVertxThread() {
    return Thread.currentThread() instanceof VertxThread;
  }
}
