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

import io.vertx.core.net.SocketAddress;

/**
 * <p>An SPI used internally by Vert.x to gather metrics on a socket transport which serves
 * as a base class for things like HttpServer and HttpClient, all of which serve connections.</p>
 *
 * <p>Transport can be TCP or QUIC.</p>
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface TransportMetrics<C> extends NetworkMetrics<C> {

  /**
   * Called when a client has connected, which is applicable for connections.<p/>
   *
   * The remote name of the client is a best effort to provide the name of the remote host, i.e. if the name
   * is specified at creation time, this name will be used otherwise it will be the remote address.
   *
   * @param remoteAddress the remote address of the client
   * @param remoteName the remote name of the client
   * @return the connection metric
   */
  default C connected(SocketAddress remoteAddress, String remoteName) {
    return null;
  }

  /**
   * Called when a client has disconnected, which is applicable for connections.
   *
   * @param connectionMetric the connection metric
   * @param remoteAddress the remote address of the client
   */
  default void disconnected(C connectionMetric, SocketAddress remoteAddress) {
  }

  /**
   * Called when a connection has opened a stream, only applicable for Quic connections
   * @param connectionMetric the connection metric
   */
  default void streamOpened(C connectionMetric) {
  }

  /**
   * Called when a connection has closed a stream, only applicable for Quic connections
   * @param connectionMetric the connection metric
   */
  default void streamClosed(C connectionMetric) {
  }
}
