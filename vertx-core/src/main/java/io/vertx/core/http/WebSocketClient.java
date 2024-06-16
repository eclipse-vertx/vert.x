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
import io.vertx.core.net.ClientSSLOptions;

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
   * Connect a WebSocket to the default client port and specified host and relative request URI.
   *
   * @param host  the host
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  default Future<WebSocket> connect(String host, String requestURI) {
    return connect(new WebSocketConnectOptions().setURI(requestURI).setHost(host));
  }

  /**
   * Connect a WebSocket to the default client port, default client host and specified, relative request URI.
   *
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  default Future<WebSocket> connect(String requestURI) {
    return connect(new WebSocketConnectOptions().setURI(requestURI));
  }

  /**
   * Connect a WebSocket with the specified options.
   *
   * @param options  the request options
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> connect(WebSocketConnectOptions options);

  /**
   * Shutdown with a 30 seconds timeout ({@code shutdown(30, TimeUnit.SECONDS)}).
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(30, TimeUnit.SECONDS);
  }

  /**
   * Close immediately ({@code shutdown(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return shutdown(0, TimeUnit.SECONDS);
  }

  /**
   * Update the client with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  default Future<Boolean> updateSSLOptions(ClientSSLOptions options) {
    return updateSSLOptions(options, false);
  }

  /**
   * <p>Update the client with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The {@code options} object is compared using its {@code equals} method against the existing options to prevent
   * an update when the objects are equals since loading options can be costly, this can happen for share TCP servers.
   * When object are equals, setting {@code force} to {@code true} forces the update.
   *
   * @param options the new SSL options
   * @param force force the update when options are equals
   * @return a future signaling the update success
   */
  Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force);

  /**
   * Initiate the client shutdown sequence.
   *
   * @return a future notified when the client is closed
   * @param timeout the amount of time after which all resources are forcibly closed
   * @param unit the of the timeout
   */
  Future<Void> shutdown(long timeout, TimeUnit unit);

}
