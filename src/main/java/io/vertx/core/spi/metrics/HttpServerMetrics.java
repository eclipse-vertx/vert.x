/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;

/**
 * The http server metrics SPI that Vert.x will use to call when each http server event occurs.<p/>
 *
 * The thread model for the http server metrics depends on the actual context thats started the server.<p/>
 *
 * <h3>Event loop context</h3>
 *
 * Unless specified otherwise, all the methods on this object including the methods inherited from the super interfaces are invoked
 * with the thread of the http server and therefore are the same than the
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
public interface HttpServerMetrics<R, W, S> extends TCPMetrics<S> {

  /**
   * Called when an http server request begins. Vert.x will invoke {@link #responseEnd} when the response has ended
   * or {@link #requestReset} if the request/response has failed before.
   *
   * @param socketMetric the socket metric
   * @param request the http server reuqest
   * @return the request metric
   */
  default R requestBegin(S socketMetric, HttpServerRequest request) {
    return null;
  }

  /**
   * Called when the http server request couldn't complete successfully, for instance the connection
   * was closed before the response was sent.
   *
   * @param requestMetric the request metric
   */
  default void requestReset(R requestMetric) {

  }

  /**
   * Called when an http server response begins.
   *
   * @param requestMetric the request metric
   * @param response the http server request
   */
  default void responseBegin(R requestMetric, HttpServerResponse response) {
  }

  /**
   * Called when an http server response is pushed.
   *
   * @param socketMetric the socket metric
   * @param method the pushed response method
   * @param uri the pushed response uri
   * @param response the http server response  @return the request metric
   */
  default R responsePushed(S socketMetric, HttpMethod method, String uri, HttpServerResponse response) {
    return null;
  }

  /**
   * Called when an http server response has ended.
   *
   * @param requestMetric the request metric
   * @param response the http server request
   */
  default void responseEnd(R requestMetric, HttpServerResponse response) {
  }

  /**
   * Called when an http server request is upgrade to a websocket.
   *
   * @param requestMetric the request metric
   * @param serverWebSocket the server web socket
   * @return the server web socket metric
   */
  default W upgrade(R requestMetric, ServerWebSocket serverWebSocket) {
    return null;
  }

  /**
   * Called when a server web socket connects.
   *
   * @param socketMetric the socket metric
   * @param serverWebSocket the server web socket
   * @return the server web socket metric
   */
  default W connected(S socketMetric, ServerWebSocket serverWebSocket) {
    return null;
  }

  /**
   * Called when the server web socket has disconnected.
   *
   * @param serverWebSocketMetric the server web socket metric
   */
  default void disconnected(W serverWebSocketMetric) {
  }
}
