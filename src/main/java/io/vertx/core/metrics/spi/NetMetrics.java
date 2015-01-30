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

package io.vertx.core.metrics.spi;

import io.vertx.core.net.SocketAddress;

/**
 * An SPI used internally by Vert.x to gather metrics on a net socket which serves
 * as a base class for things like HttpServer and HttpClient, all of which serve TCP connections.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface NetMetrics extends BaseMetrics {

  /**
   * Called when a socket is listening. For example, this is called when an http or net server
   * has been created and is listening on a specific host/port.
   *
   * @param localAddress the local address the net socket is listening on.
   */
  void listening(SocketAddress localAddress);

  /**
   * Called when a client has connected, which is applicable for TCP connections.
   *
   * @param remoteAddress the remote address of the client
   */
  void connected(SocketAddress remoteAddress);

  /**
   * Called when a client has disconnected, which is applicable for TCP connections.
   *
   * @param remoteAddress the remote address of the client
   */
  void disconnected(SocketAddress remoteAddress);

  /**
   * Called when bytes have been read
   *
   * @param remoteAddress the remote address which this socket received bytes from
   * @param numberOfBytes the number of bytes read
   */
  void bytesRead(SocketAddress remoteAddress, long numberOfBytes);

  /**
   * Called when bytes have been written
   *
   * @param remoteAddress the remote address which bytes are being written to
   * @param numberOfBytes the number of bytes written
   */
  void bytesWritten(SocketAddress remoteAddress, long numberOfBytes);

  /**
   * Called when exceptions occur for a specific connection.
   *
   * @param remoteAddress the remote address of the connection or null if it's datagram/udp
   * @param t the exception that occurred
   */
  void exceptionOccurred(SocketAddress remoteAddress, Throwable t);
}
