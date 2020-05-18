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

package io.vertx.core.spi.cluster;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;

/**
 * Used by the {@link io.vertx.core.eventbus.EventBus clustered EventBus} to select a node for a given message..
 * <p>
 * This selector is skipped only when the user raises the {@link io.vertx.core.eventbus.DeliveryOptions#setLocalOnly(boolean)} flag.
 * Consequently, implementations must be aware of local {@link io.vertx.core.eventbus.EventBus} registrations.
 */
public interface NodeSelector {

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
   * @throws IllegalArgumentException if {@link Message#isSend()} returns false
   */
  void selectForSend(Message<?> message, Promise<String> promise);

  /**
   * Select a node for publishing the given {@code message}.
   *
   * @throws IllegalArgumentException if {@link Message#isSend()} returns true
   */
  void selectForPublish(Message<?> message, Promise<Iterable<String>> promise);

  /**
   * Invoked by the {@link ClusterManager} when messaging handler registrations are added or removed.
   */
  void registrationsUpdated(RegistrationUpdateEvent event);

  /**
   * Invoked by the {@link ClusterManager} when some handler registrations have been lost.
   */
  void registrationsLost();

}
