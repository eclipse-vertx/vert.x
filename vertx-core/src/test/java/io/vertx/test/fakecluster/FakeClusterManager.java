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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.MapOptions;
import io.vertx.core.shareddata.impl.AsynchronousCounter;
import io.vertx.core.shareddata.impl.AsynchronousLock;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.VertxSPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeClusterManager implements ClusterManager {

  private static Map<String, FakeClusterManager> nodes = Collections.synchronizedMap(new LinkedHashMap<>());

  private static List<NodeListener> nodeListeners = new CopyOnWriteArrayList<>();
  private static ConcurrentMap<String, AsyncMap> asyncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, AsyncMultiMap> asyncMultiMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, AsynchronousLock> locks = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

  private volatile String nodeID;
  private volatile NodeListener nodeListener;
  private VertxInternal vertx;

  public void setVertx(VertxSPI vertx) {
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
  public <K, V> void getAsyncMultiMap(String name, MapOptions options, Handler<AsyncResult<AsyncMultiMap<K, V>>> resultHandler) {
    AsyncMultiMap<K, V> map = (AsyncMultiMap<K, V>)asyncMultiMaps.get(name);
    if (map == null) {
      map = new FakeAsyncMultiMap<>();
      AsyncMultiMap<K, V> prevMap = (AsyncMultiMap<K, V>)asyncMultiMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    AsyncMultiMap<K, V> theMap = map;
    vertx.runOnContext(v -> resultHandler.handle(Future.completedFuture(theMap)));
  }

  @Override
  public <K, V> void getAsyncMap(String name, MapOptions options, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
    AsyncMap<K, V> map = (AsyncMap<K, V>)asyncMaps.get(name);
    if (map == null) {
      map = new FakeAsyncMap<>();
      AsyncMap<K, V> prevMap = (AsyncMap<K, V>)asyncMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    AsyncMap<K, V> theMap = map;
    vertx.runOnContext(v -> resultHandler.handle(Future.completedFuture(theMap)));
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
    lock.acquire(timeout, resultHandler);
  }

  @Override
  public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
    Counter counter = new AsynchronousCounter(vertx);
    Counter prev = counters.putIfAbsent(name, counter);
    if (prev != null) {
      counter = prev;
    }
    Counter theCounter = counter;
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> resultHandler.handle(Future.completedFuture(theCounter)));
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
      resultHandler.handle(Future.completedFuture());
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
    context.runOnContext(v -> resultHandler.handle(Future.completedFuture()));
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

    @Override
    public void release() {

    }
  }

  private class FakeCounter implements Counter {

    @Override
    public void get(Handler<AsyncResult<Long>> resultHandler) {

    }

    @Override
    public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {

    }

    @Override
    public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {

    }

    @Override
    public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {

    }

    @Override
    public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {

    }

    @Override
    public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {

    }

    @Override
    public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {

    }
  }

  private class FakeAsyncMap<K, V> implements AsyncMap<K, V> {

    private Map<K, V> map = new ConcurrentHashMap<>();

    @Override
    public void get(final K k, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(() -> map.get(k), resultHandler);
    }

    @Override
    public void put(final K k, final V v, Handler<AsyncResult<Void>> resultHandler) {
      vertx.executeBlocking(() -> {
        map.put(k, v);
        return null;
      }, resultHandler);
    }

    @Override
    public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(() -> map.putIfAbsent(k, v), resultHandler);
    }

    @Override
    public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
      vertx.executeBlocking(() -> map.remove(k, v), resultHandler);
    }

    @Override
    public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(() -> map.replace(k, v), resultHandler);
    }

    @Override
    public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
      vertx.executeBlocking(() -> map.replace(k, oldValue, newValue), resultHandler);
    }

    @Override
    public void clear(Handler<AsyncResult<Void>> resultHandler) {
      vertx.executeBlocking(() -> {
        map.clear();
        return null;
      }, resultHandler);
    }

    @Override
    public void remove(final K k, Handler<AsyncResult<V>> resultHandler) {
      vertx.executeBlocking(() -> map.remove(k), resultHandler);
    }

  }

  private class FakeAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

    private ConcurrentMap<K, ChoosableSet<V>> map = new ConcurrentHashMap<>();

    @Override
    public void add(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(() -> {
        ChoosableSet<V> vals = map.get(k);
        if (vals == null) {
          vals = new ChoosableSet<>(1);
          ChoosableSet<V> prevVals = map.putIfAbsent(k, vals);
          if (prevVals != null) {
            vals = prevVals;
          }
        }
        vals.add(v);
        return null;
      }, completionHandler);
    }

    @Override
    public void get(final K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
      vertx.executeBlocking(() -> map.get(k), asyncResultHandler);
    }

    @Override
    public void remove(final K k, final V v, Handler<AsyncResult<Boolean>> completionHandler) {
      vertx.executeBlocking(() -> {
          ChoosableSet<V> vals = map.get(k);
          if (vals != null) {
            vals.remove(v);
            if (vals.isEmpty()) {
              map.remove(k);
            }
          }
          return null;
        }, completionHandler);
    }

    @Override
    public void removeAllForValue(final V v, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(() -> {
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
        return null;
      }, completionHandler);
    }
  }
}
