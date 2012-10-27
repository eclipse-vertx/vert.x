package org.vertx.java.core.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class VertxExecutors {

  public static VertxThreadPoolExecutor newResizable(int initialSize, String threadPoolName, boolean preStartThreads) {
    VertxThreadPoolExecutor executor = new VertxThreadPoolExecutor(
        initialSize, 
        initialSize, 
        60L,
        TimeUnit.SECONDS, 
        new LinkedBlockingQueue<Runnable>(),
        new VertxThreadFactory(threadPoolName));

    if (preStartThreads) {
      executor.prestartAllCoreThreads();
    }

    return executor;
  }

  public static ExecutorService newThreadPool(int initialSize, String threadPoolName, boolean preStartThreads) {
    VertxThreadPoolExecutor executor = new VertxThreadPoolExecutor(
        initialSize, 
        initialSize, 
        60L,
        TimeUnit.SECONDS, 
        new LinkedBlockingQueue<Runnable>(),
        new VertxThreadFactory(threadPoolName));

    if (preStartThreads) {
      executor.prestartAllCoreThreads();
    }

    return executor;
  }

  public static ExecutorService newCachedThreadPool(String threadPoolName) {
    VertxThreadPoolExecutor executor = new VertxThreadPoolExecutor(0, 
        Integer.MAX_VALUE,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        new VertxThreadFactory(threadPoolName));

    return executor;
}

}
