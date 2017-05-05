/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.*;
import io.vertx.core.shareddata.Lock;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/16 by zmyer
public class AsynchronousLock implements Lock {
    //所属vertx对象
    private final Vertx vertx;
    //锁等待对象集合
    private final Queue<LockWaiter> waiters = new LinkedList<>();
    //是否已经被抢占
    private boolean owned;

    public AsynchronousLock(Vertx vertx) {
        this.vertx = vertx;
    }

    // TODO: 16/12/16 by zmyer
    public void acquire(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        //获取执行上下文对象
        Context context = vertx.getOrCreateContext();
        //获取锁对象
        doAcquire(context, timeout, resultHandler);
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public synchronized void release() {
        //获取等待锁对象
        LockWaiter waiter = pollWaiters();
        if (waiter != null) {
            //获取锁对象
            waiter.acquire(this);
        } else {
            //没有抢占者
            owned = false;
        }
    }

    // TODO: 16/12/16 by zmyer
    public void doAcquire(Context context, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        synchronized (this) {
            if (!owned) {
                // We now have the lock
                owned = true;
                //申请锁对象
                lockAcquired(context, resultHandler);
            } else {
                //否则需要将其插入到等待对象列表中
                waiters.add(new LockWaiter(this, context, timeout, resultHandler));
            }
        }
    }

    // TODO: 16/12/16 by zmyer
    private void lockAcquired(Context context, Handler<AsyncResult<Lock>> resultHandler) {
        //申请锁对象
        context.runOnContext(v -> resultHandler.handle(Future.succeededFuture(this)));
    }

    // TODO: 16/12/16 by zmyer
    private LockWaiter pollWaiters() {
        while (true) {
            //在等待者列表中轮询
            LockWaiter waiter = waiters.poll();
            if (waiter == null) {
                return null;
            } else if (!waiter.timedOut) {
                //如果等待者没有超时,则返回该等待者
                return waiter;
            }
        }
    }

    // TODO: 16/12/16 by zmyer
    private static class LockWaiter {
        //锁对象
        final AsynchronousLock lock;
        //执行上下文对象
        final Context context;
        //结果处理对象
        final Handler<AsyncResult<Lock>> resultHandler;
        //超时时间
        volatile boolean timedOut;
        //是否获得锁标记
        volatile boolean acquired;

        // TODO: 16/12/16 by zmyer
        LockWaiter(AsynchronousLock lock, Context context, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
            this.lock = lock;
            this.context = context;
            this.resultHandler = resultHandler;
            if (timeout != Long.MAX_VALUE) {
                //设置等待定时器
                context.owner().setTimer(timeout, tid -> timedOut());
            }
        }

        // TODO: 16/12/16 by zmyer
        void timedOut() {
            synchronized (lock) {
                if (!acquired) {
                    //如果未获取到锁对象,进行超时处理
                    timedOut = true;
                    context.runOnContext(v -> resultHandler.handle(Future.failedFuture(new VertxException("Timed out waiting to get lock"))));
                }
            }
        }

        // TODO: 16/12/16 by zmyer
        void acquire(AsynchronousLock lock) {
            acquired = true;
            //请求锁对象
            lock.lockAcquired(context, resultHandler);
        }
    }
}
