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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SSLOptions;

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
   * Connect a WebSocket to the specified port, host and relative request URI
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  void connect(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #connect(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> connect(int port, String host, String requestURI);

  /**
   * Connect a WebSocket with the specified options.
   *
   * @param options  the request options
   */
  void connect(WebSocketConnectOptions options, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #connect(WebSocketConnectOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> connect(WebSocketConnectOptions options);

  /**
   * Update the client SSL options.
   *
   * Update only happens if the SSL options is valid.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  Future<Void> updateSSLOptions(SSLOptions options);

  /**
   * Like {@link #updateSSLOptions(SSLOptions)}  but supplying a handler that will be called when the update
   * happened (or has failed).
   *
   * @param options the new SSL options
   * @param handler the update handler
   */
  default void updateSSLOptions(SSLOptions options, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = updateSSLOptions(options);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  /**
   * Close the client. Closing will close down any pooled connections.
   * Clients should always be closed after use.
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Like {@link #close(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> close();

}
