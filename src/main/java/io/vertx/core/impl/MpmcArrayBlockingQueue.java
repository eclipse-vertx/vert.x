package io.vertx.core.impl;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

import org.jctools.queues.MpmcArrayQueue;

import io.vertx.core.VertxOptions;
import io.vertx.core.spi.WorkerExecutorFactory;

public class MpmcArrayBlockingQueue<T> extends AbstractQueue<T> implements BlockingQueue<T> {
  public static final WorkerExecutorFactory WORKER_EXECUTOR_FACTORY_INSTANCE = new WorkerExecutorFactory();

  private final AtomicReferenceArray<Thread> waitingConsumers;
  private final Queue<T> fastQueue;
  private final BlockingQueue<T> overflowQueue;

  public MpmcArrayBlockingQueue(int fastCapacity, int consumers) {
    fastQueue = new MpmcArrayQueue<>(fastCapacity);
    overflowQueue = new LinkedBlockingQueue<>();
    waitingConsumers = new AtomicReferenceArray<>(consumers);
  }

  private void addWaitingConsumer() throws InterruptedException {
    Thread currentThread = Thread.currentThread();
    for (;;) {
      for (int i = 0; i < waitingConsumers.length(); ++i) {
        if (waitingConsumers.get(i) == currentThread) {
          return;
        }
      }
      for (int i = 0; i < waitingConsumers.length(); ++i) {
        if (waitingConsumers.get(i) == null && waitingConsumers.compareAndSet(i, null, currentThread)) {
          return;
        }
      }
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      Thread.yield();
    }
  }

  private void wakeupConsumer() {
    for (int i = 0; i < waitingConsumers.length(); ++i) {
      Thread thread = waitingConsumers.get(i);
      if (thread != null && (thread = waitingConsumers.getAndSet(i, null)) != null) {
        LockSupport.unpark(thread);
        return;
      }
    }
  }

  @Override
  public Iterator<T> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return fastQueue.size() + overflowQueue.size();
  }

  @Override
  public void put(T t) throws InterruptedException {
    if (fastQueue.offer(t)) {
      wakeupConsumer();
    } else {
      overflowQueue.put(t);
    }
  }

  @Override
  public boolean offer(T t) {
    if (fastQueue.offer(t)) {
      wakeupConsumer();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
    if (fastQueue.offer(t)) {
      wakeupConsumer();
      return true;
    }
    return false;
  }

  @Override
  public T take() throws InterruptedException {
    // Reading from empty LinkedBlockingQueue is cheap (volatile read)
    T element = overflowQueue.poll();
    if (element != null) {
      return element;
    }
    while ((element = fastQueue.poll()) == null) {
      addWaitingConsumer();
      // Producer might put element to the queue and try to wake up consumers before
      // the consumer gets registered; that's why have to poll once more.
      element = fastQueue.poll();
      if (element != null) {
        return element;
      }
      LockSupport.park();
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
    }
    return element;
  }

  @Override
  public T poll() {
    T element = overflowQueue.poll();
    if (element != null) {
      return element;
    }
    return fastQueue.poll();
  }

  @Override
  public T peek() {
    T element = overflowQueue.peek();
    if (element != null) {
      return element;
    }
    return fastQueue.peek();
  }

  @Override
  public T poll(long timeout, TimeUnit unit) throws InterruptedException {
    // Reading from empty LinkedBlockingQueue is cheap (volatile read)
    T element = overflowQueue.poll();
    if (element != null) {
      return element;
    }
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while ((element = fastQueue.poll()) == null) {
      long now = System.nanoTime();
      if (now >= deadline) {
        return null;
      }
      addWaitingConsumer();
      // Producer might put element to the queue and try to wake up consumers before
      // the consumer gets registered; that's why have to poll once more.
      element = fastQueue.poll();
      if (element != null) {
        return element;
      }
      LockSupport.parkNanos(deadline - now);
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
    }
    return element;
  }

  @Override
  public int remainingCapacity() {
    return Integer.MAX_VALUE;
  }

  @Override
  public int drainTo(Collection<? super T> c) {
    T element;
    int transferred = 0;
    while ((element = overflowQueue.poll()) != null) {
      c.add(element);
      ++transferred;
    }
    while ((element = fastQueue.poll()) != null) {
      c.add(element);
      ++transferred;
    }
    return transferred;
  }

  @Override
  public int drainTo(Collection<? super T> c, int maxElements) {
    T element;
    int transferred = 0;
    while (transferred < maxElements && (element = overflowQueue.poll()) != null) {
      c.add(element);
      ++transferred;
    }
    while (transferred < maxElements && (element = fastQueue.poll()) != null) {
      c.add(element);
      ++transferred;
    }
    return transferred;
  }

  public static class WorkerExecutorFactory implements io.vertx.core.spi.WorkerExecutorFactory {
    @Override
    public ExecutorService createExecutorService(VertxOptions options, ThreadFactory threadFactory) {
      int workerPoolSize = options.getWorkerPoolSize();
      return new ThreadPoolExecutor(workerPoolSize, workerPoolSize, 0L, TimeUnit.MILLISECONDS,
                                    new MpmcArrayBlockingQueue<>(1024, workerPoolSize), threadFactory);
    }
  }
}
