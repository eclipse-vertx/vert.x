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
import io.vertx.core.spi.cluster.RegistrationListener;

/**
 * Used by the {@link io.vertx.core.eventbus.EventBus clustered EventBus} to select a node for a given message.
 * <p>
 * This selector is skipped only when the user raises the {@link io.vertx.core.eventbus.DeliveryOptions#setLocalOnly(boolean)} flag.
 * Consequently, implementations must be aware of local {@link io.vertx.core.eventbus.EventBus} registrations.
 */
public interface NodeSelector extends RegistrationListener {

  /**
   * Invoked before the {@code vertx} instance tries to join the cluster.
   */
  void init(Vertx vertx, ClusterManager clusterManager);

  /**
   * Invoked after the clustered {@link io.vertx.core.eventbus.EventBus} has started.
   */
  void eventBusStarted();

  /**
   * Select a node for sending the given {@code message}.
   *
   * <p> The provided {@code promise} needs to be completed with {@link Promise#tryComplete} and {@link Promise#tryFail}
   * as it might completed outside the selector.
   */
  void selectForSend(String address, Promise<String> promise);

  /**
   * Select a node for publishing the given {@code message}.
   *
   * <p> The provided {@code promise} needs to be completed with {@link Promise#tryComplete} and {@link Promise#tryFail}
   * as it might completed outside the selector.
   */
  void selectForPublish(String address, Promise<Iterable<String>> promise);

}
