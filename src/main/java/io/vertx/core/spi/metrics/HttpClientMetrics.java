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

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.net.SocketAddress;

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
public interface HttpClientMetrics<R, W, S, E, T> extends TCPMetrics<S> {

  /**
   * Provides metrics for a particular endpoint
   *
   * @param host the endpoint host possibly unresolved
   * @param port the endpoint port
   * @param maxPoolSize the server max pool size
   * @return the endpoint metric
   */
  default E createEndpoint(String host, int port, int maxPoolSize) {
    return null;
  }

  /**
   * Called when an endpoint is closed.
   *
   * @param host the server host
   * @param port the server port
   * @param endpointMetric the server metric returned by {@link #createEndpoint}
   */
  default void closeEndpoint(String host, int port, E endpointMetric) {
  }

  /**
   * Called when a connection is requested.
   *
   * @param endpointMetric the endpoint metric returned by {@link #createEndpoint}
   */
  default T enqueueRequest(E endpointMetric) {
    return null;
  }

  /**
   * Called when a request for connection is satisfied.
   *
   * @param endpointMetric the endpoint metric returned by {@link #createEndpoint}
   */
  default void dequeueRequest(E endpointMetric, T taskMetric) {
  }

  /**
   * Called when a connection is made to a endpoint.
   *
   * @param endpointMetric the endpoint metric
   * @param socketMetric the socket metric
   */
  default void endpointConnected(E endpointMetric, S socketMetric) {
  }

  /**
   * Called when a connection to an endpoint is closed.
   *
   * @param endpointMetric the endpoint metric
   * @param socketMetric the socket metric
   */
  default void endpointDisconnected(E endpointMetric, S socketMetric) {
  }

  /**
   * Called when an http client request begins. Vert.x will invoke {@link #requestEnd} when the request
   * has ended or {@link #requestReset} if the request/response has failed before.
   *
   *
   *
   *
   * @param endpointMetric the endpoint metric
   * @param socketMetric the socket metric
   * @param localAddress the local address
   * @param remoteAddress the remote address
   * @param request the {@link HttpClientRequest}
   * @return the request metric
   */
  default R requestBegin(E endpointMetric, S socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    return null;
  }

  /**
   * Callend when an http client request ends.
   *
   * @param requestMetric the request metric
   */
  default void requestEnd(R requestMetric) {
  }

  /**
   * Called when an http client response begins. Vert.x will invoke {@link #responseEnd} when the response has ended
   *  or {@link #requestReset} if the request/response has failed before.
   *
   * @param requestMetric the request metric
   * @param response the {@link io.vertx.core.http.HttpClientResponse}
   */
  default void responseBegin(R requestMetric, HttpClientResponse response) {
  }

  /**
   * Called when an http client response is pushed.
   *
   * @param endpointMetric the endpoint metric
   * @param socketMetric the socket metric
   * @param localAddress the local address
   * @param remoteAddress the remote address
   * @param request the http server request
   * @return the request metric
   */
  default R responsePushed(E endpointMetric, S socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    return null;
  }

  /**
   * Called when the http client request couldn't complete successfully, for instance the connection
   * was closed before the response was received.
   *
   * @param requestMetric the request metric
   */
  default void requestReset(R requestMetric) {
  }

  /**
   * Called when an http client response has ended
   *
   * @param requestMetric the request metric
   * @param response the {@link io.vertx.core.http.HttpClientResponse}
   */
  default void responseEnd(R requestMetric, HttpClientResponse response) {
  }

  /**
   * Called when a web socket connects.
   *
   * @param endpointMetric the endpoint metric
   * @param socketMetric the socket metric
   * @param webSocket the server web socket
   * @return the web socket metric
   */
  default W connected(E endpointMetric, S socketMetric, WebSocket webSocket) {
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
