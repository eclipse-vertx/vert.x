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
public interface HttpClientMetrics<R, W, S> extends TCPMetrics<S> {

  /**
   * Called when an http client request begins
   *
   * @param socketMetric the socket metric
   * @param localAddress the local address
   * @param remoteAddress the remote address
   * @param request the {@link io.vertx.core.http.HttpClientRequest}
   * @return the request metric
   */
  R requestBegin(S socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request);

  /**
   * Called when an http client response has ended
   *
   * @param requestMetric the request metric
   * @param response the {@link io.vertx.core.http.HttpClientResponse}
   */
  void responseEnd(R requestMetric, HttpClientResponse response);

  /**
   * Called when a web socket connects.
   *
   * @param socketMetric the socket metric
   * @param webSocket the server web socket
   * @return the web socket metric
   */
  W connected(S socketMetric, WebSocket webSocket);

  /**
   * Called when the web socket has disconnected.
   *
   * @param webSocketMetric the web socket metric
   */
  void disconnected(W webSocketMetric);
}
