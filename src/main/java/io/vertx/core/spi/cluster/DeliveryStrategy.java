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
import io.vertx.core.net.impl.ServerID;

import java.util.List;

/**
 * Clustered {@link io.vertx.core.eventbus.EventBus} delivery strategy.
 */
public interface DeliveryStrategy {

  /**
   * Choose nodes the given {@code message} should be delivered to.
   */
  void chooseNodes(Message<?> message, Handler<AsyncResult<List<ServerID>>> handler);

}
