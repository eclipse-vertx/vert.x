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

package org.vertx.java.fakecluster;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.VertxSPI;
import org.vertx.java.core.spi.cluster.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FakeClusterManager implements ClusterManager {

  private static Map<String, FakeClusterManager> nodes =
      Collections.synchronizedMap(new LinkedHashMap<String, FakeClusterManager>());

  private static List<NodeListener> nodeListeners = new ArrayList<>();
  private static ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, AsyncMap> asyncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, AsyncMultiMap> asyncMultiMaps = new ConcurrentHashMap<>();

  private String nodeID;
  private NodeListener nodeListener;
  private VertxSPI vertx;

  public FakeClusterManager(VertxSPI vertx) {
    this.vertx = vertx;
  }

  private static void doJoin(String nodeID, FakeClusterManager node) {
    if (nodes.containsKey(nodeID)) {
      throw new IllegalStateException("Node has already joined!");
    }
    nodes.put(nodeID, node);
    for (NodeListener listener: nodeListeners) {
      listener.nodeAdded(nodeID);
    }
  }

  private static void doLeave(String nodeID) {
    if (!nodes.containsKey(nodeID)) {
      throw new IllegalStateException("Node hasn't joined!");
    }
    nodes.remove(nodeID);
    for (NodeListener listener: nodeListeners) {
      listener.nodeLeft(nodeID);
    }

  }

  private static void doAddNodeListener(NodeListener listener) {
    if (nodeListeners.contains(listener)) {
      throw new IllegalStateException("Listener already registered!");
    }
    nodeListeners.add(listener);
  }

  private static void doRemoveNodeListener(NodeListener listener) {
    if (!nodeListeners.contains(listener)) {
      throw new IllegalStateException("Listener not registered!");
    }
    nodeListeners.remove(listener);
  }

  @Override
  public <K, V> AsyncMultiMap<K, V> getAsyncMultiMap(String name) {
    AsyncMultiMap<K, V> map = (AsyncMultiMap<K, V>)asyncMultiMaps.get(name);
    if (map == null) {
      map = new FakeAsyncMultiMap<>();
      AsyncMultiMap<K, V> prevMap = (AsyncMultiMap<K, V>)asyncMultiMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    return map;
  }

  @Override
  public <K, V> AsyncMap<K, V> getAsyncMap(String name) {
    AsyncMap<K, V> map = (AsyncMap<K, V>)asyncMaps.get(name);
    if (map == null) {
      map = new FakeAsyncMap<>();
      AsyncMap<K, V> prevMap = (AsyncMap<K, V>)asyncMaps.putIfAbsent(name, map);
      if (prevMap != null) {
        map = prevMap;
      }
    }
    return map;
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
  public void join() {
    this.nodeID = UUID.randomUUID().toString();
    doJoin(nodeID, this);
  }

  @Override
  public void leave() {
    if (nodeID == null) {
      // Not joined
      return;
    }
    if (nodeListener != null) {
      doRemoveNodeListener(nodeListener);
      nodeListener = null;
    }
    doLeave(nodeID);
    this.nodeID = null;
  }

  public static void reset() {
    nodes.clear();
    nodeListeners.clear();
    syncMaps.clear();
    asyncMaps.clear();
    asyncMultiMaps.clear();
  }

  private class FakeAsyncMap<K, V> implements AsyncMap<K, V> {

    private Map<K, V> map = new ConcurrentHashMap<>();

    @Override
    public void get(final K k, Handler<AsyncResult<V>> asyncResultHandler) {
      vertx.executeBlocking(new Action<V>() {
        public V perform() {
          return map.get(k);
        }
      }, asyncResultHandler);
    }

    @Override
    public void put(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(new Action<Void>() {
        public Void perform() {
          map.put(k, v);
          return null;
        }
      }, completionHandler);
    }

    @Override
    public void remove(final K k, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(new Action<Void>() {
        public Void perform() {
          map.remove(k);
          return null;
        }
      }, completionHandler);
    }
  }

  private class FakeAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

    private ConcurrentMap<K, ChoosableSet<V>> map = new ConcurrentHashMap<>();

    @Override
    public void add(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(new Action<Void>() {
        public Void perform() {
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
        }
      }, completionHandler);
    }

    @Override
    public void get(final K k, Handler<AsyncResult<ChoosableIterable<V>>> asyncResultHandler) {
      vertx.executeBlocking(new Action<ChoosableIterable<V>>() {
        public ChoosableIterable<V> perform() {
          return map.get(k);
        }
      }, asyncResultHandler);
    }

    @Override
    public void remove(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(new Action<Void>() {
        public Void perform() {
          ChoosableSet<V> vals = map.get(k);
          if (vals != null) {
            vals.remove(v);
            if (vals.isEmpty()) {
              map.remove(k);
            }
          }
          return null;
        }
      }, completionHandler);
    }

    @Override
    public void removeAllForValue(final V v, Handler<AsyncResult<Void>> completionHandler) {
      vertx.executeBlocking(new Action<Void>() {
        public Void perform() {
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
        }
      }, completionHandler);
    }
  }
}
