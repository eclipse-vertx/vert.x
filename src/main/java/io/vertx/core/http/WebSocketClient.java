/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.metrics.Measured;

import java.util.concurrent.TimeUnit;

/**
 * An asynchronous WebSocket client.
 * <p>
 * It allows you to open WebSockets to servers.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface WebSocketClient extends Measured {

  /**
   * Create a WebSocket that is not yet connected to the server.
   *
   * @return the client WebSocket
   */
  ClientWebSocket webSocket();

  /**
   * Connect a WebSocket to the specified port, host and relative request URI.
   *
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  default Future<WebSocket> connect(int port, String host, String requestURI) {
    return connect(new WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port));
  }

  /**
   * Connect a WebSocket with the specified options.
   *
   * @param options  the request options
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> connect(WebSocketConnectOptions options);

  /**
   * Close the client immediately ({@code close(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return shutdown(0, TimeUnit.SECONDS);
  }

  /**
   * Initiate the close sequence with a 30 seconds timeout.
   *
   * see {@link #shutdown(long, TimeUnit)}
   */
  default Future<Void> shutdown() {
    return shutdown(30, TimeUnit.SECONDS);
  }

  /**
   * Initiate the client close sequence.
   *
   * <p> Connections are taken out of service and closed when all inflight requests are processed, client connection are
   * immediately removed from the pool. When all connections are closed the client is closed. When the timeout
   * expires, all unclosed connections are immediately closed.
   *
   * <ul>
   *   <li>HTTP/2 connections will send a go away frame immediately to signal the other side the connection will close</li>
   *   <li>HTTP/1.x client connection will be closed after the current response is received</li>
   * </ul>
   *
   * @return a future notified when the client is closed
   */
  Future<Void> shutdown(long timeout, TimeUnit timeUnit);

}
