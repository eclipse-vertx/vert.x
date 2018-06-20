/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.fakecluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.impl.AsynchronousCounter;
import io.vertx.core.shareddata.impl.LocalAsyncLocks;
import io.vertx.core.shareddata.impl.LocalAsyncMapImpl;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class FakeClusterManager implements ClusterManager {

  private static Map<String, FakeClusterManager> nodes = Collections.synchronizedMap(new LinkedHashMap<>());

  private static ConcurrentMap<String, LocalAsyncMapImpl> asyncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, ConcurrentMap> asyncMultiMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
  private static LocalAsyncLocks localAsyncLocks = new LocalAsyncLocks();
  private static ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  private String nodeID;
  private NodeListener nodeListener;
  private VertxInternal vertx;

  public void setVertx(Vertx vertx) {
    this.vertx = (VertxInternal) vertx;
  }

  private static void doJoin(String nodeID, FakeClusterManager node) {
    if (nodes.containsKey(nodeID)) {
      throw new IllegalStateException("Node has already joined!");
    }
    nodes.put(nodeID, node);
    synchronized (nodes) {
      for (Entry<String, FakeClusterManager> entry : nodes.entrySet()) {
        if (!entry.getKey().equals(nodeID)) {
          new Thread(() -> entry.getValue().memberAdded(nodeID)).start();
        }
      }
    }
  }

  private synchronized void memberAdded(String nodeID) {
    if (isActive()) {
      try {
        if (nodeListener != null) {
          nodeListener.nodeAdded(nodeID);
        }
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
  }

  private static void doLeave(String nodeID) {
    nodes.remove(nodeID);
    synchronized (nodes) {
      for (Entry<String, FakeClusterManager> entry : nodes.entrySet()) {
        if (!entry.getKey().equals(nodeID)) {
          new Thread(() -> entry.getValue().memberRemoved(nodeID)).start();
        }
      }
    }
  }

  private synchronized void memberRemoved(String nodeID) {
    if (isActive()) {
      try {
        if (nodeListener != null) {
          nodeListener.nodeLeft(nodeID);
        }
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
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
    @SuppressWarnings("unchecked")
    ConcurrentMap<K, ChoosableSet<V>> theMap = map;
    vertx.runOnContext(v -> resultHandler.handle(Future.succeededFuture(new FakeAsyncMultiMap<>(theMap))));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
    LocalAsyncMapImpl<K, V> asyncMap = asyncMaps.computeIfAbsent(name, n -> new LocalAsyncMapImpl(vertx));
    vertx.runOnContext(v -> resultHandler.handle(Future.succeededFuture(asyncMap)));
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    Map map = syncMaps.get(name);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      Map prevMap = syncMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    @SuppressWarnings("unchecked")
    Map<K, V> theMap = map;
    return theMap;
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    localAsyncLocks.acquire(vertx.getOrCreateContext(), name, timeout, resultHandler);
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
    ArrayList<String> res;
    synchronized (nodes) {
      res = new ArrayList<>(nodes.keySet());
    }
    return res;
  }

  @Override
  public void nodeListener(NodeListener listener) {
    this.nodeListener = listener;
  }

  @Override
  public void join(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(fut -> {
      synchronized (this) {
        this.nodeID = UUID.randomUUID().toString();
        doJoin(nodeID, this);
      }
      fut.complete();
    }, resultHandler);
  }

  @Override
  public void leave(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(fut -> {
      synchronized (this) {
        if (nodeID != null) {
          if (nodeListener != null) {
            nodeListener = null;
          }
          doLeave(nodeID);
          this.nodeID = null;
        }
      }
      fut.complete();
    }, resultHandler);
  }

  @Override
  public boolean isActive() {
    return nodeID != null;
  }

  public static void reset() {
    nodes.clear();
    asyncMaps.clear();
    asyncMultiMaps.clear();
    localAsyncLocks = new LocalAsyncLocks();
    counters.clear();
    syncMaps.clear();
  }

  private class FakeAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

    private final ConcurrentMap<K, ChoosableSet<V>> map;
    private final TaskQueue taskQueue;

    public FakeAsyncMultiMap(ConcurrentMap<K, ChoosableSet<V>> map) {
      taskQueue = new TaskQueue();
      this.map = map;
    }

    @Override
    public void add(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
      ContextInternal ctx = vertx.getOrCreateContext();
      ctx.executeBlocking(fut -> {
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
      }, taskQueue, completionHandler);
    }

    @Override
    public void get(final K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
      ContextInternal ctx = vertx.getOrCreateContext();
      ctx.executeBlocking(fut -> {
        ChoosableIterable<V> it = map.get(k);
        if (it == null) {
          it = new ChoosableSet<>(0);
        }
        fut.complete(it);
      }, taskQueue, asyncResultHandler);
    }

    @Override
    public void remove(final K k, final V v, Handler<AsyncResult<Boolean>> completionHandler) {
      ContextInternal ctx = vertx.getOrCreateContext();
      ctx.executeBlocking(fut -> {
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
      }, taskQueue, completionHandler);
    }

    @Override
    public void removeAllForValue(final V v, Handler<AsyncResult<Void>> completionHandler) {
      removeAllMatching(v::equals, completionHandler);
    }

    @Override
    public void removeAllMatching(Predicate<V> p, Handler<AsyncResult<Void>> completionHandler) {
      ContextInternal ctx = vertx.getOrCreateContext();
      ctx.executeBlocking(fut -> {
        Iterator<Entry<K, ChoosableSet<V>>> mapIter = map.entrySet().iterator();
        while (mapIter.hasNext()) {
          Entry<K, ChoosableSet<V>> entry = mapIter.next();
          ChoosableSet<V> vals = entry.getValue();
          Iterator<V> iter = vals.iterator();
          while (iter.hasNext()) {
            V val = iter.next();
            if (p.test(val)) {
              iter.remove();
            }
          }
          if (vals.isEmpty()) {
            mapIter.remove();
          }
        }
        fut.complete();
      }, taskQueue, completionHandler);
    }
  }
}
