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
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.DeliveryStrategy;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.core.spi.cluster.impl.selectors.Selectors;

/**
 * @author Thomas Segismont
 */
public class DefaultDeliveryStrategy implements DeliveryStrategy {

  private Selectors selectors;

  @Override
  public void setVertx(Vertx vertx) {
    ClusterManager clusterManager = ((VertxInternal) vertx).getClusterManager();
    selectors = new Selectors(clusterManager);
  }

  @Override
  public void chooseForSend(Message<?> message, Promise<String> promise) {
    Arguments.require(message.isSend(), "selectForSend used for publishing");
    selectors.withSelector(message, promise, (prom, selector) -> prom.complete(selector.selectForSend()));
  }

  @Override
  public void chooseForPublish(Message<?> message, Promise<Iterable<String>> promise) {
    Arguments.require(!message.isSend(), "selectForPublish used for sending");
    selectors.withSelector(message, promise, (prom, selector) -> prom.complete(selector.selectForPublish()));
  }

  @Override
  public void registrationsUpdated(RegistrationUpdateEvent event) {
    selectors.dataReceived(event.getAddress(), event.getRegistrations());
  }
}
