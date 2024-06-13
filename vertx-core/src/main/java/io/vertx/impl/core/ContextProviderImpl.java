package io.vertx.impl.core;

import io.vertx.core.Context;
import io.vertx.core.spi.ContextProvider;
import io.vertx.internal.core.ContextInternal;

public class ContextProviderImpl implements ContextProvider {

  @Override
  public Context current() {
    return VertxImpl.currentContext();
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
