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

package io.vertx.core.spi.cluster.impl;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.Arguments;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
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
  public void selectForSend(Message<?> message, Promise<String> promise) {
    Arguments.require(message.isSend(), "selectForSend used for publishing");
    selectors.withSelector(message, promise, (prom, selector) -> prom.complete(selector.selectForSend()));
  }

  @Override
  public void selectForPublish(Message<?> message, Promise<Iterable<String>> promise) {
    Arguments.require(!message.isSend(), "selectForPublish used for sending");
    selectors.withSelector(message, promise, (prom, selector) -> prom.complete(selector.selectForPublish()));
  }

  @Override
  public void registrationsUpdated(RegistrationUpdateEvent event) {
    selectors.dataReceived(event.address(), event.registrations());
  }

  @Override
  public void registrationsLost() {
    selectors.dataLost();
  }
}
