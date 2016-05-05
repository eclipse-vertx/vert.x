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

import io.vertx.core.net.SocketAddress;

/**
 * An SPI used internally by Vert.x to gather metrics on a net socket which serves
 * as a base class for things like HttpServer and HttpClient, all of which serve TCP connections.<p/>
 * <p>
 * The thread model for the tcp metrics depends on the actual context thats created the client/server.<p/>
 * <p>
 * <h3>Event loop context</h3>
 * <p>
 * Unless specified otherwise, all the methods on this object including the methods inherited from the super interfaces are invoked
 * with the thread of the client/server and therefore are the same than the
 * {@link io.vertx.core.spi.metrics.VertxMetrics} {@code createMetrics} method that created and returned
 * this metrics object.
 * <p>
 * <h3>Worker context</h3>
 * <p>
 * Unless specified otherwise, all the methods on this object including the methods inherited from the super interfaces are invoked
 * with a worker thread.
 */
public interface TCPMetrics<S> extends NetworkMetrics<S> {

  /**
   * Called when a client has connected, which is applicable for TCP connections.<p/>
   * <p>
   * The remote name of the client is a best effort to provide the name of the remote host, i.e if the name
   * is specified at creation time, this name will be used otherwise it will be the remote address.
   *
   * @param remoteAddress the remote address of the client
   * @param remoteName    the remote name of the client
   * @return the socket metric
   */
  S connected(SocketAddress remoteAddress, String remoteName);

  /**
   * Called when a client has disconnected, which is applicable for TCP connections.
   *
   * @param socketMetric  the socket metric
   * @param remoteAddress the remote address of the client
   */
  void disconnected(S socketMetric, SocketAddress remoteAddress);

}
