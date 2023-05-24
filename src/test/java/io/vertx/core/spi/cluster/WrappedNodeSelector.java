/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import io.vertx.core.eventbus.Message;

public class WrappedNodeSelector implements NodeSelector {

  private final NodeSelector delegate;

  public WrappedNodeSelector(NodeSelector delegate) {
    this.delegate = delegate;
  }

  @Override
  public void init(Vertx vertx, ClusterManager clusterManager) {
    delegate.init(vertx, clusterManager);
  }

  @Override
  public void eventBusStarted() {
    delegate.eventBusStarted();
  }

  @Override
  public void selectForSend(String address, Promise<String> promise) {
    delegate.selectForSend(address, promise);
  }

  @Override
  public void selectForPublish(String address, Promise<Iterable<String>> promise) {
    delegate.selectForPublish(address, promise);
  }

  @Override
  public void registrationsUpdated(RegistrationUpdateEvent event) {
    delegate.registrationsUpdated(event);
  }

  @Override
  public void registrationsLost() {
    delegate.registrationsLost();
  }

  @Override
  public boolean wantsUpdatesFor(String address) {
    return delegate.wantsUpdatesFor(address);
  }
}
