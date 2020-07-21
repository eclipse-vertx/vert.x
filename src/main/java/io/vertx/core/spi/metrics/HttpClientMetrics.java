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

package io.vertx.core.spi.metrics;

import io.vertx.core.http.WebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

/**
 * The http client metrics SPI that Vert.x will use to call when http client events occur.<p/>
 *
 * The thread model for the http server metrics depends on the actual context thats started the server.<p/>
 *
 * <h3>Event loop context</h3>
 *
 * Unless specified otherwise, all the methods on this object including the methods inherited from the super interfaces are invoked
 * with the thread of the http client and therefore are the same than the
 * {@link io.vertx.core.spi.metrics.VertxMetrics} {@code createMetrics} method that created and returned
 * this metrics object.
 *
 * <h3>Worker context</h3>
 *
 * Unless specified otherwise, all the methods on this object including the methods inherited from the super interfaces are invoked
 * with a worker thread.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface HttpClientMetrics<R, W, S, T> extends TCPMetrics<S> {

  /**
   * Provides metrics for a particular endpoint
   *
   * @param remoteAddress the endpoint remote address
   * @param maxPoolSize the server max pool size
   * @return the endpoint metric
   */
  default ClientMetrics<R, T, HttpRequest, HttpResponse> createEndpointMetrics(SocketAddress remoteAddress, int maxPoolSize) {
    return null;
  }

  /**
   * Called when a connection is made to a endpoint.
   *  @param endpointMetric the endpoint metric
   *
   */
  default void endpointConnected(ClientMetrics<R, T, ?, ?> endpointMetric) {
  }

  /**
   * Called when a connection to an endpoint is closed.
   *  @param endpointMetric the endpoint metric
   *
   */
  default void endpointDisconnected(ClientMetrics<R, T, ?, ?> endpointMetric) {
  }

  /**
   * Called when a web socket connects.
   *
   * @param webSocket the server web socket
   * @return the web socket metric
   */
  default W connected(WebSocket webSocket) {
    return null;
  }

  /**
   * Called when the web socket has disconnected.
   *
   * @param webSocketMetric the web socket metric
   */
  default void disconnected(W webSocketMetric) {
  }
}
