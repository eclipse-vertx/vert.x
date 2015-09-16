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
import io.vertx.core.impl.ContextImpl;
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

public class FakeClusterManager implements ClusterManager {

  private static Map<String, FakeClusterManager> nodes = Collections.synchronizedMap(new LinkedHashMap<>());

  private static List<NodeListener> nodeListeners = new CopyOnWriteArrayList<>();
  private static ConcurrentMap<String, ConcurrentMap> asyncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, ConcurrentMap> asyncMultiMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, AsynchronousLock> locks = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  private volatile String nodeID;
  private volatile NodeListener nodeListener;
  private volatile VertxInternal vertx;

  public void setVertx(Vertx vertx) {
    this.vertx = (VertxInternal)vertx;
  }

  private static void doJoin(String nodeID, FakeClusterManager node) {
    if (nodes.containsKey(nodeID)) {
      throw new IllegalStateException("Node has already joined!");
    }
    nodes.put(nodeID, node);
    for (NodeListener listener: new ArrayList<>(nodeListeners)) {
      if (listener != null) {
        listener.nodeAdded(nodeID);
      }
    }
  }

  private static void doLeave(String nodeID) {
    nodes.remove(nodeID);
    for (NodeListener listener: new ArrayList<>(nodeListeners)) {
      if (listener != null) {
        listener.nodeLeft(nodeID);
      }
    }
  }

  private static void doAddNodeListener(NodeListener listener) {
    if (nodeListeners.contains(listener)) {
      throw new IllegalStateException("Listener already registered!");
    }
    nodeListeners.add(listener);
  }

  private static void doRemoveNodeListener(NodeListener listener) {
    nodeListeners.remove(listener);
  }

  @Override
  public <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> resultHandler) {
    ConcurrentMap map = asyncMultiMaps.get(name);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      ConcurrentMap prevMap = asyncMultiMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    ConcurrentMap<K, ChoosableSet<V>> theMap = map;
    vertx.runOnContext(v -> resultHandler.handle(Future.succeededFuture(new FakeAsyncMultiMap<>(theMap))));
  }

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

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    Map<K, V> map = (Map<K, V>)syncMaps.get(name);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      Map<K, V> prevMap = (Map<K, V>)syncMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    return map;
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    AsynchronousLock lock = new AsynchronousLock(vertx);
    AsynchronousLock prev = locks.putIfAbsent(name, lock);
    if (prev != null) {
      lock = prev;
    }
    FakeLock flock = new FakeLock(lock);
    flock.acquire(timeout, resultHandler);
  }

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

  @Override
  public void nodeListener(NodeListener listener) {
    doAddNodeListener(listener);
    this.nodeListener = listener;
  }

  @Override
  public void join(Handler<AsyncResult<Void>> resultHandler) {
    this.nodeID = UUID.randomUUID().toString();
    doJoin(nodeID, this);
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      resultHandler.handle(Future.succeededFuture());
    });
  }

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

  @Override
  public boolean isActive() {
    return nodeID != null;
  }

  public static void reset() {
    nodes.clear();
    nodeListeners.clear();
    asyncMaps.clear();
    asyncMultiMaps.clear();
    locks.clear();
    counters.clear();
    syncMaps.clear();
  }

  private class FakeLock implements Lock {

    private final AsynchronousLock delegate;

    public FakeLock(AsynchronousLock delegate) {
      this.delegate = delegate;
    }

    public void acquire(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
      Context context = vertx.getOrCreateContext();
      delegate.doAcquire(context, timeout, resultHandler);
    }

    @Override
    public void release() {
      delegate.release();
    }
  }

  private class FakeAsyncMap<K, V> implements AsyncMap<K, V> {

    private final Map<K, V> map;

    public FakeAsyncMap(Map<K, V> map) {
      this.map = map;
    }

    @Override
    public void get(final K k, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.get(k)), resultHandler);
    }

    @Override
    public void put(final K k, final V v, Handler<AsyncResult<Void>> resultHandler) {
      vertx.executeBlocking(fut -> {
        map.put(k, v);
        fut.complete();
      }, resultHandler);
    }

    @Override
    public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.putIfAbsent(k, v)), resultHandler);
    }

    @Override
    public void put(K k, V v, long timeout, Handler<AsyncResult<Void>> completionHandler) {
      put(k, v, completionHandler);
      vertx.setTimer(timeout, tid -> map.remove(k));
    }

    @Override
    public void putIfAbsent(K k, V v, long timeout, Handler<AsyncResult<V>> completionHandler) {
      putIfAbsent(k, v, completionHandler);
    }

    @Override
    public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.remove(k, v)), resultHandler);
    }

    @Override
    public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.replace(k, v)), resultHandler);
    }

    @Override
    public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.replace(k, oldValue, newValue)), resultHandler);
    }

    @Override
    public void clear(Handler<AsyncResult<Void>> resultHandler) {
      vertx.executeBlocking(fut -> {
        map.clear();
        fut.complete();
      }, resultHandler);
    }

    @Override
    public void size(Handler<AsyncResult<Integer>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.size()), resultHandler);
    }

    @Override
    public void remove(final K k, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(fut -> fut.complete(map.remove(k)), resultHandler);
    }

  }

  private class FakeAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {


    private final ConcurrentMap<K, ChoosableSet<V>> map;

    public FakeAsyncMultiMap(ConcurrentMap<K, ChoosableSet<V>> map) {
      this.map = map;
    }

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

    @Override
    public void get(final K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
      ContextImpl ctx = (ContextImpl)Vertx.currentContext();
      vertx.executeBlocking(fut -> {
        ChoosableIterable<V> it = map.get(k);
        if (it == null) {
          it = new ChoosableSet<V>(0);
        }
        fut.complete(it);
      }, asyncResultHandler);
    }

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
