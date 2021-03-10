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
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.spi.VertxServiceProvider;

/**
 * Used by the {@link io.vertx.core.eventbus.EventBus clustered EventBus} to select a node for a given message.
 * <p>
 * This selector is skipped only when the user raises the {@link io.vertx.core.eventbus.DeliveryOptions#setLocalOnly(boolean)} flag.
 * Consequently, implementations must be aware of local {@link io.vertx.core.eventbus.EventBus} registrations.
 */
public interface NodeSelector extends VertxServiceProvider {

  @Override
  default void init(VertxBuilder builder) {
    if (builder.clusterNodeSelector() == null) {
      builder.clusterNodeSelector(this);
    }
  }

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
   *
   * @throws IllegalArgumentException if {@link Message#isSend()} returns {@code false}
   */
  void selectForSend(Message<?> message, Promise<String> promise);

  /**
   * Select a node for publishing the given {@code message}.
   *
   * <p> The provided {@code promise} needs to be completed with {@link Promise#tryComplete} and {@link Promise#tryFail}
   * as it might completed outside the selector.
   *
   * @throws IllegalArgumentException if {@link Message#isSend()} returns {@code true}
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

  /**
   * Invoked by the {@link ClusterManager} to determine if the node selector wants updates for the given {@code address}.
   *
   * @param address the event bus address
   * @return {@code true} if the node selector wants updates for the given {@code address}, {@code false} otherwise
   */
  default boolean wantsUpdatesFor(String address) {
    return true;
  }

}
