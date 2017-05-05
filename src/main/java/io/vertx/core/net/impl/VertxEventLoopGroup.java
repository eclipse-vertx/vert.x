/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.netty.channel.*;
import io.netty.util.concurrent.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 17/1/1 by zmyer
@SuppressWarnings("deprecation")
public final class VertxEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {
    //事件循环处理器索引
    private int pos;
    //事件循环处理器集合
    private final List<EventLoopHolder> workers = new ArrayList<>();
    //屏障对象
    private final CountDownLatch latch = new CountDownLatch(1);
    //关闭标记
    private final AtomicBoolean gracefulShutdown = new AtomicBoolean();
    //异步关闭对象
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized EventLoop next() {
        //事件循环处理器集合为空,则直接异常
        if (workers.isEmpty()) {
            throw new IllegalStateException();
        } else {
            //获取事件循环处理器对象
            EventLoop worker = workers.get(pos).worker;
            //递增索引
            pos++;
            //开始检查索引合法性
            checkPos();
            //返回事件循环处理器
            return worker;
        }
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public Iterator<EventExecutor> iterator() {
        return children.iterator();
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return next().register(promise);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public boolean isShutdown() {
        return latch.getCount() == 0;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public boolean isTerminated() {
        return isShutdown();
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    // TODO: 17/1/1 by zmyer
    public synchronized void addWorker(EventLoop worker) {
        EventLoopHolder holder = findHolder(worker);
        if (holder == null) {
            workers.add(new EventLoopHolder(worker));
        } else {
            holder.count++;
        }
    }

    // TODO: 17/1/1 by zmyer
    public synchronized void shutdown() {
        for (EventLoopHolder holder : workers) {
            holder.worker.shutdown();
        }
        latch.countDown();
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public boolean isShuttingDown() {
        return gracefulShutdown.get();
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (gracefulShutdown.compareAndSet(false, true)) {
            //统计事件循环处理器数量
            final AtomicInteger counter = new AtomicInteger(workers.size());
            for (EventLoopHolder holder : workers) {
                // We don't use a lambda here just to keep IntelliJ happy as it (incorrectly) flags a syntax error
                // here
                //开始关闭每个事件循环处理器,并注册监听器
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

    // TODO: 17/1/1 by zmyer
    private EventLoopHolder findHolder(EventLoop worker) {
        //创建事件循环处理器封装对象
        EventLoopHolder wh = new EventLoopHolder(worker);
        for (EventLoopHolder holder : workers) {
            //开始对比每个事件循环对象,返回符合条件的时间循环处理器
            if (holder.equals(wh)) {
                return holder;
            }
        }
        return null;
    }

    // TODO: 17/1/1 by zmyer
    public synchronized void removeWorker(EventLoop worker) {
        //TODO can be optimised
        //查找时间循环处理器
        EventLoopHolder holder = findHolder(worker);
        if (holder != null) {
            //递减引用计数器
            holder.count--;
            if (holder.count == 0) {
                //如果引用计数器为0,则需要将其删除
                workers.remove(holder);
            }
            //检查索引
            checkPos();
        } else {
            throw new IllegalStateException("Can't find worker to remove");
        }
    }

    public synchronized int workerCount() {
        return workers.size();
    }

    // TODO: 17/1/1 by zmyer
    private void checkPos() {
        if (pos == workers.size()) {
            pos = 0;
        }
    }

    // TODO: 17/1/1 by zmyer
    private static class EventLoopHolder {
        //引用计数器
        int count = 1;
        //事件循环处理器对象
        final EventLoop worker;

        EventLoopHolder(EventLoop worker) {
            this.worker = worker;
        }

        // TODO: 17/1/1 by zmyer
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

    //事件执行器集合
    private final Set<EventExecutor> children = new Set<EventExecutor>() {
        @Override
        public Iterator<EventExecutor> iterator() {
            return new EventLoopIterator(workers.iterator());
        }

        @Override
        public int size() {
            return workers.size();
        }

        @Override
        public boolean isEmpty() {
            return workers.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return workers.contains(o);
        }

        @Override
        public Object[] toArray() {
            return workers.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return workers.toArray(a);
        }

        @Override
        public boolean add(EventExecutor eventExecutor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return workers.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends EventExecutor> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    };

    // TODO: 17/1/1 by zmyer
    private static final class EventLoopIterator implements Iterator<EventExecutor> {
        //事件循环处理器集合迭代器
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
