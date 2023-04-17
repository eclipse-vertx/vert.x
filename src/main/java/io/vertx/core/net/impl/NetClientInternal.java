/*
 * Copyright (c) 2011-20123Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.concurrent.TimeUnit;

public interface NetClientInternal extends NetClient, MetricsProvider, Closeable {

  /**
   * Open a socket to the {@code remoteAddress} server.
   *
   * @param proxyOptions optional proxy configuration
   * @param remoteAddress the server address
   * @param peerAddress the peer address (along with SSL)
   * @param serverName the SNI server name (along with SSL)
   * @param ssl whether to use SSL
   * @param useAlpn wether to use ALPN (along with SSL)
   * @param registerWriteHandlers whether to register event-bus write handlers
   * @param connectHandler the promise to resolve with the connect result
   * @param context the socket context
   * @param remainingAttempts how many times reconnection is reattempted
   */
  void connectInternal(ProxyOptions proxyOptions,
                              SocketAddress remoteAddress,
                              SocketAddress peerAddress,
                              String serverName,
                              boolean ssl,
                              boolean useAlpn,
                              boolean registerWriteHandlers,
                              Promise<NetSocket> connectHandler,
                              ContextInternal context,
                              int remainingAttempts);

  @Override
  default Future<Void> close() {
    return close(0L, TimeUnit.SECONDS);
  }

  Future<Void> closeFuture();

  /**
   * Shutdown the client, a {@link ShutdownEvent} is broadcast to all channels. The operation completes
   * when all channels are closed or the timeout expires.
   *
   * @param timeout the shutdown timeout
   * @param timeUnit the shutdown timeout unit
   * @return a future completed when all channels are closed or the timeout expires.
   */
  Future<Void> shutdown(long timeout, TimeUnit timeUnit);

  Future<Void> close(long timeout, TimeUnit timeUnit);

}
