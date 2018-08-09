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

import io.vertx.core.net.SocketAddress;

/**
 * An SPI used internally by Vert.x to gather metrics on a net socket which serves
 * as a base class for things like HttpServer and HttpClient, all of which serve TCP connections.<p/>
 *
 * The thread model for the tcp metrics depends on the actual context thats created the client/server.<p/>
 *
 * <h3>Event loop context</h3>
 *
 * Unless specified otherwise, all the methods on this object including the methods inherited from the super interfaces are invoked
 * with the thread of the client/server and therefore are the same than the
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
public interface TCPMetrics<S> extends NetworkMetrics<S> {

  /**
   * Called when a client has connected, which is applicable for TCP connections.<p/>
   *
   * The remote name of the client is a best effort to provide the name of the remote host, i.e if the name
   * is specified at creation time, this name will be used otherwise it will be the remote address.
   *
   * @param remoteAddress the remote address of the client
   * @param remoteName the remote name of the client
   * @return the socket metric
   */
  default S connected(SocketAddress remoteAddress, String remoteName) {
    return null;
  }

  /**
   * Called when a client has disconnected, which is applicable for TCP connections.
   *
   * @param socketMetric the socket metric
   * @param remoteAddress the remote address of the client
   */
  default void disconnected(S socketMetric, SocketAddress remoteAddress) {
  }

}
