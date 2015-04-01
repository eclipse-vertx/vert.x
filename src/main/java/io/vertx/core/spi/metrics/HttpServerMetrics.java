/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;

/**
 * The http server metrics SPI that Vert.x will use to call when each http server event occurs.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface HttpServerMetrics<R, W, S> extends TCPMetrics<S> {

  /**
   * Called when an http server request begins
   *
   * @param socketMetric the socket metric
   * @param request the {@link io.vertx.core.http.HttpServerRequest}
   * @return the request metric
   */
  R requestBegin(S socketMetric, HttpServerRequest request);

  /**
   * Called when an http server response has ended.
   *
   * @param requestMetric the request metric
   * @param response the {@link io.vertx.core.http.HttpServerResponse}
   */
  void responseEnd(R requestMetric, HttpServerResponse response);

  /**
   * Called when a server web socket connects.
   *
   * @param socketMetric the socket metric
   * @param serverWebSocket the server web socket
   * @return the server web socket metric
   */
  W connected(S socketMetric, ServerWebSocket serverWebSocket);

  /**
   * Called when the server web socket has disconnected.
   *
   * @param serverWebSocketMetric the server web socket metric
   */
  void disconnected(W serverWebSocketMetric);
}
