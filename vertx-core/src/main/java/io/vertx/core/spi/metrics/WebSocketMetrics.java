/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.metrics;

import io.vertx.core.spi.observability.HttpRequest;

/**
 * WebSocket metrics.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface WebSocketMetrics<W> extends Metrics {

  /**
   * Called when a server web socket connects.
   *
   * @param request the observable request
   * @return the server web socket metric
   */
  default W connected(HttpRequest request) {
    return null;
  }

  /**
   * Called when the server web socket has disconnected.
   *
   * @param webSocketMetric the server web socket metric
   */
  default void disconnected(W webSocketMetric) {
  }
}
