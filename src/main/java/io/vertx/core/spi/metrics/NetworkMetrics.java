/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.net.SocketAddress;

/**
 * An SPI used internally by Vert.x to gather metrics on a net socket which serves
 * as a base class for TCP or UDP.<p/>
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface NetworkMetrics<S> extends Metrics {

  /**
   * Called when bytes have been read
   *
   * @param socketMetric the socket metric, null for UDP
   * @param remoteAddress the remote address which this socket received bytes from
   * @param numberOfBytes the number of bytes read
   */
  void bytesRead(S socketMetric, SocketAddress remoteAddress, long numberOfBytes);

  /**
   * Called when bytes have been written
   *
   * @param socketMetric the socket metric, null for UDP
   * @param remoteAddress the remote address which bytes are being written to
   * @param numberOfBytes the number of bytes written
   */
  void bytesWritten(S socketMetric, SocketAddress remoteAddress, long numberOfBytes);

  /**
   * Called when exceptions occur for a specific connection.
   *
   * @param socketMetric the socket metric, null for UDP
   * @param remoteAddress the remote address of the connection or null if it's datagram/udp
   * @param t the exception that occurred
   */
  void exceptionOccurred(S socketMetric, SocketAddress remoteAddress, Throwable t);
}
