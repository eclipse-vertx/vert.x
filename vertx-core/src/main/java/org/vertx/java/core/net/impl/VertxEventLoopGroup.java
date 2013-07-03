/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import io.netty.channel.*;
import io.netty.util.concurrent.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@SuppressWarnings("deprecation")
public final class VertxEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {

  private int pos;

  private final List<EventLoopHolder> workers = new ArrayList<>();
  private final CountDownLatch latch = new CountDownLatch(1);
  private final AtomicBoolean gracefulShutdown = new AtomicBoolean();
  private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

  @Override
  public synchronized EventLoop next() {
    if (workers.isEmpty()) {
      throw new IllegalStateException();
    } else {
      EventLoop worker = workers.get(pos).worker;
      pos++;
      checkPos();
      return worker;
    }
  }

  @Override
  public Iterator<EventExecutor> iterator() {
    return new EventLoopIterator(workers.iterator());
  }

  @Override
  public ChannelFuture register(Channel channel) {
    return next().register(channel);
  }

  @Override
  public ChannelFuture register(Channel channel, ChannelPromise promise) {
    return next().register(channel, promise);
  }

  @Override
  public boolean isShutdown() {
    return latch.getCount() == 0;
  }

  @Override
  public boolean isTerminated() {
    return isShutdown();
  }

  @Override
  public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return latch.await(timeout, unit);
  }


  public synchronized void addWorker(EventLoop worker) {
    EventLoopHolder holder = findHolder(worker);
    if (holder == null) {
      workers.add(new EventLoopHolder(worker));
    } else {
      holder.count++;
    }
  }

  public synchronized void shutdown() {
    for (EventLoopHolder holder : workers) {
      holder.worker.shutdown();
    }
    latch.countDown();
  }

  @Override
  public boolean isShuttingDown() {
    return gracefulShutdown.get();
  }

  @Override
  public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
    if (gracefulShutdown.compareAndSet(false, true)) {
      final AtomicInteger counter = new AtomicInteger(workers.size());
      for (EventLoopHolder holder : workers) {
        holder.worker.shutdownGracefully().addListener(new GenericFutureListener() {
          @Override
          public void operationComplete(Future future) throws Exception {
            if (counter.decrementAndGet() == 0) {
              terminationFuture.setSuccess(null);
            }
          }
        });
      }
    }
    return terminationFuture;
  }

  @Override
  public Future<?> terminationFuture() {
    return terminationFuture;
  }

  private EventLoopHolder findHolder(EventLoop worker) {
    EventLoopHolder wh = new EventLoopHolder(worker);
    for (EventLoopHolder holder : workers) {
      if (holder.equals(wh)) {
        return holder;
      }
    }
    return null;
  }

  public synchronized void removeWorker(EventLoop worker) {
    //TODO can be optimised
    EventLoopHolder holder = findHolder(worker);
    if (holder != null) {
      holder.count--;
      if (holder.count == 0) {
        workers.remove(holder);
      }
      checkPos();
    } else {
      throw new IllegalStateException("Can't find worker to remove");
    }
  }

  public synchronized int workerCount() {
    return workers.size();
  }

  private void checkPos() {
    if (pos == workers.size()) {
      pos = 0;
    }
  }

  private static class EventLoopHolder {
    int count = 1;
    final EventLoop worker;

    EventLoopHolder(EventLoop worker) {
      this.worker = worker;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EventLoopHolder that = (EventLoopHolder) o;

      if (worker != null ? !worker.equals(that.worker) : that.worker != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return worker != null ? worker.hashCode() : 0;
    }
  }

  private static final class EventLoopIterator implements Iterator<EventExecutor> {
    private final Iterator<EventLoopHolder> holderIt;

    public EventLoopIterator(Iterator<EventLoopHolder> holderIt) {
      this.holderIt = holderIt;
    }

    @Override
    public boolean hasNext() {
      return holderIt.hasNext();
    }

    @Override
    public EventExecutor next() {
      return holderIt.next().worker;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("read-only");
    }
  }
}
