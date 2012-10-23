package org.vertx.java.core.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class VertxThreadPoolExecutor extends ThreadPoolExecutor implements ExecutorServiceMXBean {

  private AtomicLong waitingTaskCount = new AtomicLong(0L);

  public VertxThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
  }

  @Override
  public int getWorkQueueSize() {
    return super.getQueue().size();
  }

  @Override
  public long getWaitingTasks() {
    return waitingTaskCount.get();
  }

  @Override
  public boolean remove(Runnable task) {
    waitingTaskCount.decrementAndGet();
    return super.remove(task);
  }

//  @Override
//  protected void beforeExecute(Thread t, Runnable r) {
//    // TODO Auto-generated method stub
//    super.beforeExecute(t, r);
//  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);
    waitingTaskCount.decrementAndGet();
  }

  @Override
  public void execute(Runnable command) {
    waitingTaskCount.incrementAndGet();
    super.execute(command);
  }

  @Override
  public Future<?> submit(Runnable task) {
    waitingTaskCount.incrementAndGet();
    return super.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    waitingTaskCount.incrementAndGet();
    return super.submit(task, result);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    waitingTaskCount.incrementAndGet();
    return super.submit(task);
  }

}
