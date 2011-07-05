package org.nodex.core;

import org.nodex.core.util.OrderedExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:39
 * To change this template use File | Settings | File Templates.
 */
public final class Nodex {

  private int backgroundPoolSize = 20;
  private int corePoolSize = Runtime.getRuntime().availableProcessors();
  private ExecutorService backgroundPool;
  private ExecutorService corePool;
  private ExecutorService acceptorPool;

  public static Nodex instance = new Nodex();

  public synchronized void setCoreThreadPoolSize(int size) {
    if (corePool != null) {
      throw new IllegalStateException("Cannot set core pool size after pool has been created");
    }
    corePoolSize = size;
  }

  public synchronized int getCoreThreadPoolSize() {
    return corePoolSize;
  }

  public synchronized void setBackgroundThreadPoolSize(int size) {
    if (backgroundPool != null) {
      throw new IllegalStateException("Cannot set background size after pool has been created");
    }
    backgroundPoolSize = size;
  }

  public synchronized int getBackgroundThreadPoolSize() {
    return backgroundPoolSize;
  }

  public void executeInBackground(Runnable task) {
    getBackgroundPool().execute(task);
  }

  //TODO need some way of hiding this from API - we should have an SPI interface where this is exposed

  //The background pool is used for making blocking calls to legacy synchronous APIs, or for running long
  //running tasks
  public synchronized Executor getBackgroundPool() {
    if (backgroundPool == null) {
      backgroundPool = Executors.newFixedThreadPool(backgroundPoolSize, new NodeThreadFactory("node.x-background-thread-"));
    }
    return backgroundPool;
  }


  //The worker pool is fixed size with an unbounded feed queue
  //By default we initialise it to a size equal to number of cores
  public synchronized Executor getCorePool() {
    if (corePool == null) {
      corePool = Executors.newFixedThreadPool(corePoolSize, new NodeThreadFactory("node.x-core-thread-"));
    }
    return corePool;
  }

  //We use a cache pool, but it will never get large since only used for acceptors.
  //There will be one thread for each port listening on
  public synchronized Executor getAcceptorPool() {
    if (acceptorPool == null) {
      final AtomicInteger threadCount = new AtomicInteger(0);
      corePool = Executors.newCachedThreadPool(
          new ThreadFactory() {
            public Thread newThread(Runnable runnable) {
              return new Thread(runnable, "node.x-acceptor-thread-" + threadCount.getAndIncrement());
            }
          });
    }
    return corePool;
  }

  public Executor getOrderedBackgroundExecutor() {
    return new OrderedExecutor(getBackgroundPool());
  }

  public int setTimeout(Callback<?> callback, long delay) {
    return -1;
  }

  public int setPeriodic(Callback<?> callback, long delay, long period) {
    return -1;
  }

  public void cancelTimeout(int timeoutID) {

  }

  public void runDeferred(Runnable runnable) {
    getCorePool().execute(runnable);
  }

  private static class NodeThreadFactory implements ThreadFactory {

    private String prefix;
    private AtomicInteger threadCount = new AtomicInteger(0);

    NodeThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    public Thread newThread(Runnable runnable) {
      Thread t = new Thread(runnable, prefix + threadCount.getAndIncrement());
      t.setDaemon(true);
      return t;
    }
  }
}
