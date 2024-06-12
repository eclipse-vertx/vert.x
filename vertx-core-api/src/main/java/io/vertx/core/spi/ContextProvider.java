package io.vertx.core.spi;

import io.vertx.core.Context;
import io.vertx.core.ServiceHelper;

public interface ContextProvider {

  ContextProvider INSTANCE = ServiceHelper.loadFactory(ContextProvider.class);

  /**
   * Gets the current context
   *
   * @return The current context or {@code null} if there is no current context
   */
  Context current();

  /**
   * Is the current thread a worker thread?
   * <p>
   * NOTE! This is not always the same as calling {@link Context#isWorkerContext}. If you are running blocking code
   * from an event loop context, then this will return true but {@link Context#isWorkerContext} will return false.
   *
   * @return true if current thread is a worker thread, false otherwise
   */
  boolean isOnWorkerThread();

  /**
   * Is the current thread an event thread?
   * <p>
   * NOTE! This is not always the same as calling {@link Context#isEventLoopContext}. If you are running blocking code
   * from an event loop context, then this will return false but {@link Context#isEventLoopContext} will return true.
   *
   * @return true if current thread is an event thread, false otherwise
   */
  boolean isOnEventLoopThread();

  /**
   * Is the current thread a Vert.x thread? That's either a worker thread or an event loop thread
   *
   * @return true if current thread is a Vert.x thread, false otherwise
   */
  boolean isOnVertxThread();

}
