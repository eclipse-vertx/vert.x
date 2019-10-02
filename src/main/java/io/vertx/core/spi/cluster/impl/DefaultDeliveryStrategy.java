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

package io.vertx.core.spi.cluster.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.spi.cluster.DeliveryStrategy;
import io.vertx.core.spi.cluster.NodeInfo;

import java.util.List;

/**
 * Default {@link DeliveryStrategy}.
 *
 * @author Thomas Segismont
 */
public class DefaultDeliveryStrategy implements DeliveryStrategy {

  @Override
  public void chooseNodes(Message<?> message, Handler<AsyncResult<List<NodeInfo>>> handler) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
