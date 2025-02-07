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

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Completable;
import io.vertx.core.Promise;
import io.vertx.core.spi.cluster.ClusteredNode;
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
  void init(ClusteredNode clusterManager);

  /**
   * Invoked after the clustered {@link io.vertx.core.eventbus.EventBus} has started.
   */
  void eventBusStarted();

  /**
   * Select a node for sending the given {@code message}.
   *
   * <p> The provided {@code promise} succeeds with a node id of selected node among the list of nodes available for the
   * provided {@code address}, the value is {@code null} when the list of nodes is empty or is not know by the cluster
   * manager.</p>
   *
   * <p> The provided {@code promise} needs to be completed with {@link Promise#tryComplete} and {@link Promise#tryFail}
   * as it might be completed outside the selector.</p>
   *
   */
  void selectForSend(String address, Completable<String> promise);

  /**
   * Select a node for publishing the given {@code message}.
   *
   * <p> The provided {@code promise} needs to be completed with {@link Promise#tryComplete} and {@link Promise#tryFail}
   * as it might completed outside the selector.
   */
  void selectForPublish(String address, Completable<Iterable<String>> promise);

}
