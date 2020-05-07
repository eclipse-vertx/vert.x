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
 * {@link io.vertx.core.eventbus.EventBus Clustered EventBus (CEB)} delivery strategy.
 * <p>
 * This strategy is skipped by the <em>CEB</em> only when the user raises the {@link io.vertx.core.eventbus.DeliveryOptions#setLocalOnly(boolean)} flag.
 * Consequently, implementations must be aware of local {@link io.vertx.core.eventbus.EventBus} registrations.
 */
public interface DeliveryStrategy {

  /**
   * Invoked after the {@link io.vertx.core.eventbus.EventBus Clustered EventBus} has started.
   */
  void init(Vertx vertx);

  /**
   * Choose a node for sending the given {@code message}.
   *
   * @throws IllegalArgumentException if {@link Message#isSend()} returns false
   */
  void chooseForSend(Message<?> message, Promise<String> promise);

  /**
   * Choose a node for publishing the given {@code message}.
   *
   * @throws IllegalArgumentException if {@link Message#isSend()} returns true
   */
  void chooseForPublish(Message<?> message, Promise<Iterable<String>> promise);

  /**
   * Invoked by the {@link ClusterManager} when messaging handler registrations are added or removed.
   */
  void registrationsUpdated(RegistrationUpdateEvent event);

}
