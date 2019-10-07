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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.impl.AsynchronousCounter;
import io.vertx.core.shareddata.impl.LocalAsyncLocks;
import io.vertx.core.shareddata.impl.LocalAsyncMapImpl;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationStream;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class FakeClusterManager implements ClusterManager {

  private static Map<String, FakeClusterManager> nodes = Collections.synchronizedMap(new LinkedHashMap<>());

  private static ConcurrentMap<String, List<RegistrationInfo>> registrations = new ConcurrentHashMap<>();

  private static ConcurrentMap<String, LocalAsyncMapImpl> asyncMaps = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, Map> syncMaps = new ConcurrentHashMap<>();
  private static LocalAsyncLocks localAsyncLocks = new LocalAsyncLocks();
  private static ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  private String nodeID;
  private NodeListener nodeListener;
  private VertxInternal vertx;

  public void setVertx(VertxInternal vertx) {
    this.vertx = vertx;
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
  @SuppressWarnings("unchecked")
  public <K, V> Future<AsyncMap<K, V>> getAsyncMap(String name) {
    LocalAsyncMapImpl<K, V> asyncMap = asyncMaps.computeIfAbsent(name, n -> new LocalAsyncMapImpl(vertx));
    ContextInternal context = vertx.getOrCreateContext();
    return context.succeededFuture(asyncMap);
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
  public Future<Lock> getLockWithTimeout(String name, long timeout) {
    return localAsyncLocks.acquire(vertx.getOrCreateContext(), name, timeout);
  }

  @Override
  public Future<Counter> getCounter(String name) {
    AtomicLong counter = new AtomicLong();
    AtomicLong prev = counters.putIfAbsent(name, counter);
    if (prev != null) {
      counter = prev;
    }
    AtomicLong theCounter = counter;
    ContextInternal context = vertx.getOrCreateContext();
    return context.succeededFuture(new AsynchronousCounter(vertx, theCounter));
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
    registrations.forEach((address, registrationInfos) -> {
      synchronized (registrationInfos) {
        registrationInfos.removeIf(registrationInfo -> registrationInfo.getNodeInfo().getNodeId().equals(nodeID));
      }
    });
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

  @Override
  public void register(RegistrationInfo registrationInfo, Handler<AsyncResult<Void>> completionHandler) {
    registrations.computeIfAbsent(registrationInfo.getAddress(), k -> Collections.synchronizedList(new ArrayList<>()))
      .add(registrationInfo);
    vertx.getOrCreateContext().runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
  }

  @Override
  public void unregister(RegistrationInfo registrationInfo, Handler<AsyncResult<Void>> completionHandler) {
    Future<Void> result;
    if (registrations.get(registrationInfo.getAddress()).remove(registrationInfo)) {
      result = Future.succeededFuture();
    } else {
      result = Future.failedFuture("Registration not found");
    }
    vertx.getOrCreateContext().runOnContext(v -> completionHandler.handle(result));
  }

  @Override
  public void registrationListener(String address, Handler<AsyncResult<RegistrationStream>> resultHandler) {
    FakeRegistrationStream stream = new FakeRegistrationStream(address);
    vertx.getOrCreateContext().runOnContext(v -> {
      resultHandler.handle(Future.succeededFuture(stream));
    });
  }

  public static void reset() {
    registrations.clear();
    nodes.clear();
    asyncMaps.clear();
    localAsyncLocks = new LocalAsyncLocks();
    counters.clear();
    syncMaps.clear();
  }

  private static class FakeRegistrationStream implements RegistrationStream {

    static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
      Runtime.getRuntime().availableProcessors(),
      r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
      });

    private final String address;
    private final List<RegistrationInfo> initialState;
    private final ScheduledFuture<?> scheduledFuture;

    private long demand = 0;
    private List<RegistrationInfo> lastState;
    private List<RegistrationInfo> newState;
    private Handler<List<RegistrationInfo>> handler;

    FakeRegistrationStream(String address) {
      this.address = address;
      List<RegistrationInfo> registrationInfos = registrations.get(address);
      if (registrationInfos == null) {
        initialState = Collections.emptyList();
      } else {
        synchronized (registrationInfos) {
          initialState = Collections.unmodifiableList(new ArrayList<>(registrationInfos));
        }
      }
      lastState = newState = initialState;
      scheduledFuture = executorService.scheduleWithFixedDelay(this::checkUpdate, 5, 5, TimeUnit.MILLISECONDS);
    }

    private void checkUpdate() {
      List<RegistrationInfo> registrationInfos = registrations.get(address);
      List<RegistrationInfo> ns;
      if (registrationInfos == null) {
        ns = Collections.emptyList();
      } else {
        synchronized (registrationInfos) {
          ns = Collections.unmodifiableList(new ArrayList<>(registrationInfos));
        }
      }
      synchronized (this) {
        newState = ns;
      }
      emit();
    }

    @Override
    public String address() {
      return address;
    }

    @Override
    public List<RegistrationInfo> initialState() {
      return initialState;
    }

    @Override
    public RegistrationStream exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    @Override
    public synchronized RegistrationStream handler(Handler<List<RegistrationInfo>> handler) {
      synchronized (this) {
        this.handler = handler;
        if (handler == null) {
          demand = 0;
        }
      }
      if (handler != null) {
        emit();
      }
      return this;
    }

    @Override
    public synchronized RegistrationStream pause() {
      demand = 0;
      return this;
    }

    @Override
    public RegistrationStream resume() {
      return fetch(Long.MAX_VALUE);
    }

    @Override
    public RegistrationStream fetch(long amount) {
      synchronized (this) {
        try {
          demand = Math.addExact(demand, amount);
        } catch (ArithmeticException ignore) {
          demand = Long.MAX_VALUE;
        }
      }
      emit();
      return this;
    }

    private void emit() {
      Runnable emission;
      synchronized (this) {
        if (demand < 1 || handler == null || lastState.equals(newState)) {
          emission = null;
        } else {
          lastState = newState;
          emission = () -> handler.handle(lastState);
          if (demand != Long.MAX_VALUE) {
            demand--;
          }
        }
      }
      if (emission != null) {
        emission.run();
      }
    }

    @Override
    public RegistrationStream endHandler(Handler<Void> endHandler) {
      return this;
    }

    @Override
    public void close() {
      scheduledFuture.cancel(false);
      handler(null);
    }
  }
}
