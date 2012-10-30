package org.vertx.java.core.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

public class VertxThreadPoolExecutor extends ThreadPoolExecutor implements ExecutorServiceMXBean, NotificationBroadcaster {

  private final NotificationBroadcasterSupport support = new NotificationBroadcasterSupport();

  private final AtomicLong sequence = new AtomicLong(0L);

  private AtomicLong waitingTaskCount = new AtomicLong(0L);

  public static final String QUEUE_NOT_ZERO = "queue.not.zero";

  public VertxThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
  }

  @Override
  public void addNotificationListener(NotificationListener listener,
      NotificationFilter filter, Object handback)
      throws IllegalArgumentException {
    support.addNotificationListener(listener, filter, handback);
  }

  @Override
  public void removeNotificationListener(NotificationListener listener)
      throws ListenerNotFoundException {
    support.removeNotificationListener(listener);
  }

  @Override
  public MBeanNotificationInfo[] getNotificationInfo() {
    return support.getNotificationInfo();
  }

  private void fireQueueNotification(long size) {
    long timestamp = System.currentTimeMillis();
    String msg = String.format("Executor queue size is %d", size);
    Notification notification = new Notification(QUEUE_NOT_ZERO, this, sequence.incrementAndGet(), timestamp, msg);
    support.sendNotification(notification);
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

    // rough stab at an event if the queue is larger than 1
    long size = waitingTaskCount.get();
    if (size > 1) {
      fireQueueNotification(size);
    }
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
