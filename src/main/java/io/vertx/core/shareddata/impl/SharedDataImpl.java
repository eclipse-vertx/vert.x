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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.*;
import io.vertx.core.spi.cluster.ClusterManager;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/15 by zmyer
public class SharedDataImpl implements SharedData {
    //默认锁定超时时间
    private static final long DEFAULT_LOCK_TIMEOUT = 10 * 1000;
    //所属vertx对象
    private final VertxInternal vertx;
    //集群管理器
    private final ClusterManager clusterManager;
    //本地锁映射表
    private final ConcurrentMap<String, AsynchronousLock> localLocks = new ConcurrentHashMap<>();
    //本地引用计数器表
    private final ConcurrentMap<String, Counter> localCounters = new ConcurrentHashMap<>();
    //
    private final ConcurrentMap<Object, LocalMap<?, ?>> localMaps = new ConcurrentHashMap<>();

    // TODO: 16/12/15 by zmyer
    public SharedDataImpl(VertxInternal vertx, ClusterManager clusterManager) {
        this.vertx = vertx;
        this.clusterManager = clusterManager;
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resultHandler, "resultHandler");
        if (clusterManager == null) {
            throw new IllegalStateException("Can't get cluster wide map if not clustered");
        }

        //从集群管理器只中读取映射表
        clusterManager.<K, V>getAsyncMap(name, ar -> {
            if (ar.succeeded()) {
                // Wrap it
                //结果处理
                resultHandler.handle(Future.succeededFuture(new WrappedAsyncMap<K, V>(ar.result())));
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public void getLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resultHandler, "resultHandler");
        getLockWithTimeout(name, DEFAULT_LOCK_TIMEOUT, resultHandler);
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resultHandler, "resultHandler");
        Arguments.require(timeout >= 0, "timeout must be >= 0");
        if (clusterManager == null) {
            //获取本地锁
            getLocalLock(name, timeout, resultHandler);
        } else {
            //从集群管理器中获取锁
            clusterManager.getLockWithTimeout(name, timeout, resultHandler);
        }
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resultHandler, "resultHandler");
        if (clusterManager == null) {
            //获取本地引用计数器
            getLocalCounter(name, resultHandler);
        } else {
            //从集群管理器中读取引用计算器
            clusterManager.getCounter(name, resultHandler);
        }
    }

    /**
     * Return a {@code Map} with the specific {@code name}. All invocations of this method with the same value of {@code name}
     * are guaranteed to return the same {@code Map} instance. <p>
     */
    // TODO: 16/12/15 by zmyer
    @SuppressWarnings("unchecked")
    public <K, V> LocalMap<K, V> getLocalMap(String name) {
        //首先从本地映射表中读取指定的map对象
        LocalMap<K, V> map = (LocalMap<K, V>) localMaps.get(name);
        if (map == null) {
            //如果本地不存在具体的名称的map对象,则需要重新创建
            map = new LocalMapImpl<>(name, localMaps);
            //将新创建的map对象插入都映射表中
            LocalMap prev = localMaps.putIfAbsent(name, map);
            if (prev != null) {
                map = prev;
            }
        }
        return map;
    }

    // TODO: 16/12/16 by zmyer
    private void getLocalLock(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        //首先构造异步锁
        AsynchronousLock lock = new AsynchronousLock(vertx);
        //将创建的异步锁注册到本地锁映射表中
        AsynchronousLock prev = localLocks.putIfAbsent(name, lock);
        if (prev != null) {
            lock = prev;
        }
        //开始请求获取指定的锁对象
        lock.acquire(timeout, resultHandler);
    }

    // TODO: 16/12/16 by zmyer
    private void getLocalCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        //创建异步引用计算器
        Counter counter = new AsynchronousCounter(vertx);
        //注册引用计数器
        Counter prev = localCounters.putIfAbsent(name, counter);
        if (prev != null) {
            counter = prev;
        }
        Counter theCounter = counter;
        //读取执行上下文对象
        Context context = vertx.getOrCreateContext();
        //开始在上下文中执行具体的任务
        context.runOnContext(v -> resultHandler.handle(Future.succeededFuture(theCounter)));
    }

    // TODO: 16/12/16 by zmyer
    private static void checkType(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot put null in key or value of cluster wide map");
        }
        Class<?> clazz = obj.getClass();
        if (clazz == Integer.class || clazz == int.class ||
                clazz == Long.class || clazz == long.class ||
                clazz == Short.class || clazz == short.class ||
                clazz == Float.class || clazz == float.class ||
                clazz == Double.class || clazz == double.class ||
                clazz == Boolean.class || clazz == boolean.class ||
                clazz == Byte.class || clazz == byte.class ||
                clazz == String.class || clazz == byte[].class) {
            // Basic types - can go in as is
            return;
        } else if (obj instanceof ClusterSerializable) {
            // OK
            return;
        } else if (obj instanceof Serializable) {
            // OK
            return;
        } else {
            throw new IllegalArgumentException("Invalid type: " + clazz + " to put in cluster wide map");
        }
    }

    // TODO: 16/12/16 by zmyer
    private static class WrappedAsyncMap<K, V> implements AsyncMap<K, V> {
        //异步map对象
        private final AsyncMap<K, V> delegate;

        WrappedAsyncMap(AsyncMap<K, V> other) {
            this.delegate = other;
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void get(K k, Handler<AsyncResult<V>> asyncResultHandler) {
            delegate.get(k, asyncResultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
            checkType(k);
            checkType(v);
            delegate.put(k, v, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void put(K k, V v, long timeout, Handler<AsyncResult<Void>> completionHandler) {
            checkType(k);
            checkType(v);
            delegate.put(k, v, timeout, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler) {
            checkType(k);
            checkType(v);
            delegate.putIfAbsent(k, v, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void putIfAbsent(K k, V v, long timeout, Handler<AsyncResult<V>> completionHandler) {
            checkType(k);
            checkType(v);
            delegate.putIfAbsent(k, v, timeout, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void remove(K k, Handler<AsyncResult<V>> resultHandler) {
            delegate.remove(k, resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
            delegate.removeIfPresent(k, v, resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
            delegate.replace(k, v, resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
            delegate.replaceIfPresent(k, oldValue, newValue, resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void clear(Handler<AsyncResult<Void>> resultHandler) {
            delegate.clear(resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void size(Handler<AsyncResult<Integer>> resultHandler) {
            delegate.size(resultHandler);
        }
    }

}
