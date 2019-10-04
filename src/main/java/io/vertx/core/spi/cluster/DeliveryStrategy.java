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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.VertxInternal;

import java.util.List;

/**
 * {@link io.vertx.core.eventbus.EventBus Clustered EventBus (CEB)} delivery strategy.
 * <p>
 * The <em>CEB</em> uses this regardless of the messaging paradigm: send, publish or request/reply.
 * When a node is chosen, implementations should return a list holding:
 * <ul>
 *   <li>at most one value when {{@link Message#isSend()}} returns {@code true}</li>
 *   <li>zero, one or more values when {{@link Message#isSend()}} returns {@code false}</li>
 * </ul>
 * <p>
 * This strategy is skipped by the <em>CEB</em> only when the user raises the {@link io.vertx.core.eventbus.DeliveryOptions#setLocalOnly(boolean)} flag.
 * Consequently, implementations must be aware of local {@link io.vertx.core.eventbus.EventBus} registrations.
 */
public interface DeliveryStrategy {

  /**
   * Invoked after the cluster manager has started.
   */
  void setVertx(VertxInternal vertx);

  /**
   * Invoked after the {@link io.vertx.core.eventbus.EventBus} has started
   */
  void setNodeInfo(NodeInfo nodeInfo);

  /**
   * Choose nodes the given {@code message} should be delivered to.
   */
  void chooseNodes(Message<?> message, Handler<AsyncResult<List<NodeInfo>>> handler);

}
