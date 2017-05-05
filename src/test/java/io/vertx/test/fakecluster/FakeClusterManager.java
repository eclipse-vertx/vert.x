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

package io.vertx.test.fakecluster;

import io.vertx.core.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.impl.AsynchronousCounter;
import io.vertx.core.shareddata.impl.AsynchronousLock;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

// TODO: 16/12/16 by zmyer
public class FakeClusterManager implements ClusterManager {
    //节点对象映射表
    private static Map<String, FakeClusterManager> nodes = Collections.synchronizedMap(new LinkedHashMap<>());
    //节点对象监听器集合
    private static List<NodeListener> nodeListeners = new CopyOnWriteArrayList<>();
    //异步map集合
    private static ConcurrentMap<String, ConcurrentMap> asyncMaps = new ConcurrentHashMap<>();
    //异步multimap集合
    private static ConcurrentMap<String, ConcurrentMap> asyncMultiMaps = new ConcurrentHashMap<>();
    //同步map集合
    private static ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
    //锁对象集合
    private static ConcurrentMap<String, AsynchronousLock> locks = new ConcurrentHashMap<>();
    //引用计数器集合
    private static ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    //节点id
    private volatile String nodeID;
    //节点监听器
    private volatile NodeListener nodeListener;
    //所属的vertx对象
    private volatile VertxInternal vertx;

    // TODO: 16/12/16 by zmyer
    public void setVertx(Vertx vertx) {
        this.vertx = (VertxInternal) vertx;
    }

    // TODO: 16/12/16 by zmyer
    private static void doJoin(String nodeID, FakeClusterManager node) {
        //如果待加入的节点已经在集合中,则直返回
        if (nodes.containsKey(nodeID)) {
            throw new IllegalStateException("Node has already joined!");
        }
        //在节点集合中注册
        nodes.put(nodeID, node);

        //将新加入的节点注册到每个节点监听器中
        for (NodeListener listener : new ArrayList<>(nodeListeners)) {
            if (listener != null) {
                listener.nodeAdded(nodeID);
            }
        }
    }

    // TODO: 16/12/16 by zmyer
    private static void doLeave(String nodeID) {
        //从节点集群中删除指定的节点
        nodes.remove(nodeID);
        for (NodeListener listener : new ArrayList<>(nodeListeners)) {
            if (listener != null) {
                //从每个节点监听器中删除节点
                listener.nodeLeft(nodeID);
            }
        }
    }

    // TODO: 16/12/16 by zmyer
    private static void doAddNodeListener(NodeListener listener) {
        if (nodeListeners.contains(listener)) {
            throw new IllegalStateException("Listener already registered!");
        }
        //注册节点监听器
        nodeListeners.add(listener);
    }

    // TODO: 16/12/16 by zmyer
    private static void doRemoveNodeListener(NodeListener listener) {
        nodeListeners.remove(listener);
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> resultHandler) {
        //从异步map集合中读取指定的map对象
        ConcurrentMap map = asyncMultiMaps.get(name);
        if (map == null) {
            //如果没有,则需要创建
            map = new ConcurrentHashMap<>();
            //将创建的map插入到集合中
            ConcurrentMap prevMap = asyncMultiMaps.putIfAbsent(name, map);
            if (prevMap != null) {
                map = prevMap;
            }
        }
        //
        ConcurrentMap<K, ChoosableSet<V>> theMap = map;
        //开始在上下文对象中启动任务
        vertx.runOnContext(v -> resultHandler.handle(Future.succeededFuture(new FakeAsyncMultiMap<>(theMap))));
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        ConcurrentMap map = asyncMaps.get(name);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            ConcurrentMap prevMap = asyncMaps.putIfAbsent(name, map);
            if (prevMap != null) {
                map = prevMap;
            }
        }
        ConcurrentMap<K, V> theMap = map;
        vertx.runOnContext(v -> resultHandler.handle(Future.succeededFuture(new FakeAsyncMap<>(theMap))));
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public <K, V> Map<K, V> getSyncMap(String name) {
        Map<K, V> map = (Map<K, V>) syncMaps.get(name);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            Map<K, V> prevMap = (Map<K, V>) syncMaps.putIfAbsent(name, map);
            if (prevMap != null) {
                map = prevMap;
            }
        }
        return map;
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        AsynchronousLock lock = new AsynchronousLock(vertx);
        AsynchronousLock prev = locks.putIfAbsent(name, lock);
        if (prev != null) {
            lock = prev;
        }
        FakeLock flock = new FakeLock(lock);
        //请求锁对象
        flock.acquire(timeout, resultHandler);
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        AtomicLong counter = new AtomicLong();
        AtomicLong prev = counters.putIfAbsent(name, counter);
        if (prev != null) {
            counter = prev;
        }
        AtomicLong theCounter = counter;
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> resultHandler.handle(Future.succeededFuture(new AsynchronousCounter(vertx, theCounter))));
    }

    @Override
    public String getNodeID() {
        return nodeID;
    }

    @Override
    public List<String> getNodes() {
        return new ArrayList<>(nodes.keySet());
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public void nodeListener(NodeListener listener) {
        doAddNodeListener(listener);
        this.nodeListener = listener;
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public void join(Handler<AsyncResult<Void>> resultHandler) {
        this.nodeID = UUID.randomUUID().toString();
        //添加新的节点
        doJoin(nodeID, this);
        //读取上下文对象
        Context context = vertx.getOrCreateContext();
        //运行上下文对象
        context.runOnContext(v -> {
            resultHandler.handle(Future.succeededFuture());
        });
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public void leave(Handler<AsyncResult<Void>> resultHandler) {
        if (nodeID != null) {
            if (nodeListener != null) {
                doRemoveNodeListener(nodeListener);
                nodeListener = null;
            }
            doLeave(nodeID);
            this.nodeID = null;
        }
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> resultHandler.handle(Future.succeededFuture()));
    }

    // TODO: 16/12/16 by zmyer
    @Override
    public boolean isActive() {
        return nodeID != null;
    }

    // TODO: 16/12/16 by zmyer
    public static void reset() {
        nodes.clear();
        nodeListeners.clear();
        asyncMaps.clear();
        asyncMultiMaps.clear();
        locks.clear();
        counters.clear();
        syncMaps.clear();
    }

    // TODO: 16/12/16 by zmyer
    private class FakeLock implements Lock {
        //异步锁对象
        private final AsynchronousLock delegate;

        public FakeLock(AsynchronousLock delegate) {
            this.delegate = delegate;
        }

        //请求锁对象
        public void acquire(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
            Context context = vertx.getOrCreateContext();
            delegate.doAcquire(context, timeout, resultHandler);
        }

        //释放锁对象
        @Override
        public void release() {
            delegate.release();
        }
    }

    // TODO: 16/12/16 by zmyer
    private class FakeAsyncMap<K, V> implements AsyncMap<K, V> {
        //map对象
        private final Map<K, V> map;

        public FakeAsyncMap(Map<K, V> map) {
            this.map = map;
        }

        //// TODO: 16/12/16 by zmyer
        @Override
        public void get(final K k, Handler<AsyncResult<V>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.get(k)), resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void put(final K k, final V v, Handler<AsyncResult<Void>> resultHandler) {
            vertx.executeBlocking(fut -> {
                map.put(k, v);
                fut.complete();
            }, resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.putIfAbsent(k, v)), resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void put(K k, V v, long timeout, Handler<AsyncResult<Void>> completionHandler) {
            put(k, v, completionHandler);
            vertx.setTimer(timeout, tid -> map.remove(k));
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void putIfAbsent(K k, V v, long timeout, Handler<AsyncResult<V>> completionHandler) {
            putIfAbsent(k, v, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.remove(k, v)), resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.replace(k, v)), resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.replace(k, oldValue, newValue)), resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void clear(Handler<AsyncResult<Void>> resultHandler) {
            vertx.executeBlocking(fut -> {
                map.clear();
                fut.complete();
            }, resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void size(Handler<AsyncResult<Integer>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.size()), resultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void remove(final K k, Handler<AsyncResult<V>> resultHandler) {
            vertx.executeBlocking(fut -> fut.complete(map.remove(k)), resultHandler);
        }

    }

    // TODO: 16/12/16 by zmyer
    private class FakeAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {
        //map对象
        private final ConcurrentMap<K, ChoosableSet<V>> map;

        // TODO: 16/12/16 by zmyer
        public FakeAsyncMultiMap(ConcurrentMap<K, ChoosableSet<V>> map) {
            this.map = map;
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void add(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
            vertx.executeBlocking(fut -> {
                ChoosableSet<V> vals = map.get(k);
                if (vals == null) {
                    vals = new ChoosableSet<>(1);
                    ChoosableSet<V> prevVals = map.putIfAbsent(k, vals);
                    if (prevVals != null) {
                        vals = prevVals;
                    }
                }
                vals.add(v);
                fut.complete();
            }, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void get(final K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
            vertx.executeBlocking(fut -> {
                ChoosableIterable<V> it = map.get(k);
                if (it == null) {
                    it = new ChoosableSet<V>(0);
                }
                fut.complete(it);
            }, asyncResultHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void remove(final K k, final V v, Handler<AsyncResult<Boolean>> completionHandler) {
            vertx.executeBlocking(fut -> {
                ChoosableSet<V> vals = map.get(k);
                boolean found = false;
                if (vals != null) {
                    boolean removed = vals.remove(v);
                    if (removed) {
                        if (vals.isEmpty()) {
                            map.remove(k);
                        }
                        found = true;
                    }
                }
                fut.complete(found);
            }, completionHandler);
        }

        // TODO: 16/12/16 by zmyer
        @Override
        public void removeAllForValue(final V v, Handler<AsyncResult<Void>> completionHandler) {
            vertx.executeBlocking(fut -> {
                Iterator<Map.Entry<K, ChoosableSet<V>>> mapIter = map.entrySet().iterator();
                while (mapIter.hasNext()) {
                    Map.Entry<K, ChoosableSet<V>> entry = mapIter.next();
                    ChoosableSet<V> vals = entry.getValue();
                    Iterator<V> iter = vals.iterator();
                    while (iter.hasNext()) {
                        V val = iter.next();
                        if (val.equals(v)) {
                            iter.remove();
                        }
                    }
                    if (vals.isEmpty()) {
                        mapIter.remove();
                    }
                }
                fut.complete();
            }, completionHandler);
        }
    }
}
