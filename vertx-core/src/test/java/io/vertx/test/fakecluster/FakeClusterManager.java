/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.fakecluster;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.impl.AsynchronousCounter;
import io.vertx.core.shareddata.impl.LocalAsyncLocks;
import io.vertx.core.shareddata.impl.LocalAsyncMapImpl;
import io.vertx.core.spi.cluster.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FakeClusterManager implements ClusterManager {

  private static final Map<String, FakeClusterManager> nodes = Collections.synchronizedMap(new LinkedHashMap<>());

  private static final ConcurrentMap<String, List<RegistrationInfo>> registrations = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, NodeInfo> nodeInfos = new ConcurrentHashMap<>();

  private static final ConcurrentMap<String, LocalAsyncMapImpl> asyncMaps = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
  private static LocalAsyncLocks localAsyncLocks = new LocalAsyncLocks();
  private static final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  private volatile String nodeID;
  private NodeListener nodeListener;
  private RegistrationListener registrationListener;
  private VertxInternal vertx;
  private volatile long getRegistrationsLatency = 0L;

  @Override
  public void init(Vertx vertx) {
    this.vertx = (VertxInternal) vertx;
  }

  public long getRegistrationsLatency() {
    return getRegistrationsLatency;
  }

  public void getRegistrationsLatency(long getRegistrationsLatency) {
    this.getRegistrationsLatency = getRegistrationsLatency;
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
  public <K, V> void getAsyncMap(String name, Promise<AsyncMap<K, V>> promise) {
    promise.complete(asyncMaps.computeIfAbsent(name, n -> new LocalAsyncMapImpl(vertx)));
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
    Map<K, V> theMap = map;
    return theMap;
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Promise<Lock> promise) {
    localAsyncLocks.acquire(vertx.getOrCreateContext(), name, timeout).onComplete(promise);
  }

  @Override
  public void getCounter(String name, Promise<Counter> promise) {
    promise.complete(new AsynchronousCounter(vertx, counters.computeIfAbsent(name, k -> new AtomicLong())));
  }

  @Override
  public String getNodeId() {
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
  public void setNodeInfo(NodeInfo nodeInfo, Promise<Void> promise) {
    nodeInfos.put(nodeID, nodeInfo);
    promise.complete();
  }

  @Override
  public NodeInfo getNodeInfo() {
    return nodeInfos.get(nodeID);
  }

  @Override
  public void getNodeInfo(String nodeId, Promise<NodeInfo> promise) {
    NodeInfo result = nodeInfos.get(nodeId);
    if (result != null) {
      promise.complete(result);
    } else {
      promise.fail("Not a member of the cluster");
    }
  }

  @Override
  public void join(Promise<Void> promise) {
    vertx.<Void>executeBlocking(() -> {
      synchronized (this) {
        this.nodeID = UUID.randomUUID().toString();
        doJoin(nodeID, this);
      }
      return null;
    }).onComplete(promise);
  }

  @Override
  public void leave(Promise<Void> promise) {
    List<RegistrationUpdateEvent> events = new ArrayList<>();
    registrations.keySet().forEach(address -> {
      List<RegistrationInfo> current = registrations.compute(address, (addr, infos) -> {
        List<RegistrationInfo> list = new ArrayList<>();
        if (infos != null) {
          for (RegistrationInfo info : infos) {
            if (!info.nodeId().equals(nodeID)) {
              list.add(info);
            }
          }
        }
        return list.isEmpty() ? null : list;
      });
      events.add(new RegistrationUpdateEvent(address, current));
    });
    fireRegistrationUpdateEvents(events, true);
    vertx.<Void>executeBlocking(() -> {
      synchronized (this) {
        if (nodeID != null) {
          nodeInfos.remove(nodeID);
          if (registrationListener != null) {
            registrationListener = null;
          }
          doLeave(nodeID);
          this.nodeID = null;
        }
      }
      return null;
    }).onComplete(promise);
  }

  private synchronized void fireRegistrationUpdateEvents(List<RegistrationUpdateEvent> events, boolean skipThisNode) {
    for (String nid : getNodes()) {
      if (skipThisNode && Objects.equals(nodeID, nid)) {
        continue;
      }
      for (RegistrationUpdateEvent event : events) {
        FakeClusterManager clusterManager = nodes.get(nid);
        if (clusterManager != null && clusterManager.isActive()) {
          clusterManager.registrationListener.registrationsUpdated(event);
        }
      }
    }
  }

  @Override
  public boolean isActive() {
    return nodeID != null;
  }

  @Override
  public void registrationListener(RegistrationListener registrationListener) {
    this.registrationListener = registrationListener;
  }

  @Override
  public void addRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
    List<RegistrationInfo> current = registrations.compute(address, (addrr, infos) -> {
      List<RegistrationInfo> res;
      if (infos == null) {
        res = new ArrayList<>();
      } else {
        res = new ArrayList<>(infos);
      }
      res.add(registrationInfo);
      return res;
    });
    promise.complete();
    RegistrationUpdateEvent event = new RegistrationUpdateEvent(address, current);
    fireRegistrationUpdateEvents(Collections.singletonList(event), false);
  }

  @Override
  public void removeRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
    List<RegistrationInfo> current = registrations.compute(address, (addrr, infos) -> {
      List<RegistrationInfo> list = new ArrayList<>();
      if (infos != null) {
        for (RegistrationInfo info : infos) {
          if (!Objects.equals(registrationInfo, info)) {
            list.add(info);
          }
        }
      }
      return list.isEmpty() ? null : list;
    });
    promise.complete();
    RegistrationUpdateEvent event = new RegistrationUpdateEvent(address, current);
    fireRegistrationUpdateEvents(Collections.singletonList(event), false);
  }

  @Override
  public void getRegistrations(String address, Promise<List<RegistrationInfo>> promise) {
    long delay = getRegistrationsLatency;
    if (delay > 0L) {
      vertx
        .timer(delay)
        .map(v -> registrations.get(address))
        .onComplete(promise);
    } else {
      promise.succeed(registrations.get(address));
    }
  }

  public static void reset() {
    registrations.clear();
    nodes.clear();
    asyncMaps.clear();
    localAsyncLocks = new LocalAsyncLocks();
    counters.clear();
    syncMaps.clear();
  }
}
