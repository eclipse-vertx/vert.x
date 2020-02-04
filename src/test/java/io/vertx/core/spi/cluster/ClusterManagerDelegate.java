/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;

import java.util.List;
import java.util.Map;

public class ClusterManagerDelegate implements ClusterManager {

  private final ClusterManager clusterManager;

  public ClusterManagerDelegate(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
  }

  @Override
  public void setVertx(VertxInternal vertx) {
    clusterManager.setVertx(vertx);
  }

  @Override
  public <K, V> Future<AsyncMap<K, V>> getAsyncMap(String name) {
    return clusterManager.getAsyncMap(name);
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    return clusterManager.getSyncMap(name);
  }

  @Override
  public Future<Lock> getLockWithTimeout(String name, long timeout) {
    return clusterManager.getLockWithTimeout(name, timeout);
  }

  @Override
  public Future<Counter> getCounter(String name) {
    return clusterManager.getCounter(name);
  }

  @Override
  public String getNodeID() {
    return clusterManager.getNodeID();
  }

  @Override
  public List<String> getNodes() {
    return clusterManager.getNodes();
  }

  @Override
  public void nodeListener(NodeListener listener) {
    clusterManager.nodeListener(listener);
  }

  @Override
  public void join(Handler<AsyncResult<Void>> resultHandler) {
    clusterManager.join(resultHandler);
  }

  @Override
  public void leave(Handler<AsyncResult<Void>> resultHandler) {
    clusterManager.leave(resultHandler);
  }

  @Override
  public boolean isActive() {
    return clusterManager.isActive();
  }

  @Override
  public Future<Void> register(RegistrationInfo registrationInfo) {
    return clusterManager.register(registrationInfo);
  }

  @Override
  public Future<Void> unregister(RegistrationInfo registrationInfo) {
    return clusterManager.unregister(registrationInfo);
  }

  @Override
  public Future<RegistrationStream> registrationListener(String address) {
    return clusterManager.registrationListener(address);
  }
}
