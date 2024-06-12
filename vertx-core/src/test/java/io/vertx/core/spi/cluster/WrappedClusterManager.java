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

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;

import java.util.List;
import java.util.Map;

public class WrappedClusterManager implements ClusterManager {

  private final ClusterManager delegate;

  public WrappedClusterManager(ClusterManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public void init(Vertx vertx, NodeSelector nodeSelector) {
    delegate.init(vertx, nodeSelector);
  }

  @Override
  public <K, V> void getAsyncMap(String name, Promise<AsyncMap<K, V>> promise) {
    delegate.getAsyncMap(name, promise);
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    return delegate.getSyncMap(name);
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Promise<Lock> promise) {
    delegate.getLockWithTimeout(name, timeout, promise);
  }

  @Override
  public void getCounter(String name, Promise<Counter> promise) {
    delegate.getCounter(name, promise);
  }

  @Override
  public String getNodeId() {
    return delegate.getNodeId();
  }

  @Override
  public List<String> getNodes() {
    return delegate.getNodes();
  }

  @Override
  public void nodeListener(NodeListener listener) {
    delegate.nodeListener(listener);
  }

  @Override
  public void setNodeInfo(NodeInfo nodeInfo, Promise<Void> promise) {
    delegate.setNodeInfo(nodeInfo, promise);
  }

  @Override
  public NodeInfo getNodeInfo() {
    return delegate.getNodeInfo();
  }

  @Override
  public void getNodeInfo(String nodeId, Promise<NodeInfo> promise) {
    delegate.getNodeInfo(nodeId, promise);
  }

  @Override
  public void join(Promise<Void> promise) {
    delegate.join(promise);
  }

  @Override
  public void leave(Promise<Void> promise) {
    delegate.leave(promise);
  }

  @Override
  public boolean isActive() {
    return delegate.isActive();
  }

  @Override
  public void addRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
    delegate.addRegistration(address, registrationInfo, promise);
  }

  @Override
  public void removeRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
    delegate.removeRegistration(address, registrationInfo, promise);
  }

  @Override
  public void getRegistrations(String address, Promise<List<RegistrationInfo>> promise) {
    delegate.getRegistrations(address, promise);
  }

  @Override
  public String clusterHost() {
    return delegate.clusterHost();
  }

  @Override
  public String clusterPublicHost() {
    return delegate.clusterPublicHost();
  }

  public ClusterManager getDelegate() {
    return delegate;
  }
}
