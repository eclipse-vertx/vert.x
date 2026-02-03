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
 * An SPI used internally by Vert.x to gather metrics on a net socket which serves
 * as a base class for TCP, UDP or QUIC.<p/>
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface NetworkMetrics<C> extends Metrics {

  /**
   * Called when bytes have been read
   *
   * @param connectionMetric the connection metric, {@code null} for UDP
   * @param remoteAddress the remote address which this socket received bytes from
   * @param numberOfBytes the number of bytes read
   */
  default void bytesRead(C connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
  }

  /**
   * Called when bytes have been written
   *
   * @param connectionMetric the connection metric,  {@code null} for UDP
   * @param remoteAddress the remote address which bytes are being written to
   * @param numberOfBytes the number of bytes written
   */
  default void bytesWritten(C connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
  }

  /**
   * Called when exceptions occur for a specific connection.
   *
   * @param connectionMetric the connection metric,  {@code null} for UDP
   * @param remoteAddress the remote address of the connection or  {@code null} if it's datagram/udp
   * @param err the exception that occurred
   */
  default void exceptionOccurred(C connectionMetric, SocketAddress remoteAddress, Throwable err) {
  }
}
