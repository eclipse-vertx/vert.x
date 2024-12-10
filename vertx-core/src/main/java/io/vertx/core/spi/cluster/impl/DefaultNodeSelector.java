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

package io.vertx.core.spi.cluster.impl;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.core.spi.cluster.impl.selector.Selectors;

/**
 * @author Thomas Segismont
 */
public class DefaultNodeSelector implements NodeSelector {

  private Selectors selectors;

  @Override
  public void init(Vertx vertx, ClusterManager clusterManager) {
    selectors = new Selectors(clusterManager);
  }

  @Override
  public void eventBusStarted() {
  }

  @Override
  public void selectForSend(String address, Promise<String> promise) {
    selectors.withSelector(address, promise, (prom, selector) -> {
      prom.tryComplete(selector.selectForSend());
    });
  }

  @Override
  public void selectForPublish(String address, Promise<Iterable<String>> promise) {
    selectors.withSelector(address, promise, (prom, selector) -> {
      prom.tryComplete(selector.selectForPublish());
    });
  }

  @Override
  public void registrationsUpdated(RegistrationUpdateEvent event) {
    selectors.dataReceived(event.address(), event.registrations(), true);
  }

  @Override
  public void registrationsLost() {
    selectors.dataLost();
  }

  @Override
  public boolean wantsUpdatesFor(String address) {
    return selectors.hasEntryFor(address);
  }
}
