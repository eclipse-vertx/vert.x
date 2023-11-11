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
   * Create a WebSocket that is not yet connected to the server.
   *
   * @return the client WebSocket
   */
  ClientWebSocket webSocket();

  /**
   * Connect a WebSocket to the specified port, host and relative request URI
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  default void connect(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    connect(new WebSocketConnectOptions().setPort(port).setHost(host).setURI(requestURI), handler);
  }

  /**
   * Like {@link #connect(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<WebSocket> connect(int port, String host, String requestURI) {
    return connect(new WebSocketConnectOptions().setPort(port).setHost(host).setURI(requestURI));
  }

  /**
   * Connect a WebSocket to the default client port and specified host and relative request URI.
   *
   * @param host  the host
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  default void connect(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    connect(new WebSocketConnectOptions().setURI(requestURI).setHost(host), handler);
  }

  /**
   * Like {@link #connect(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<WebSocket> connect(String host, String requestURI) {
    return connect(new WebSocketConnectOptions().setURI(requestURI).setHost(host));
  }

  /**
   * Connect a WebSocket to the default client port, default client host and specified, relative request URI.
   *
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  default void connect(String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    connect(new WebSocketConnectOptions().setURI(requestURI), handler);
  }

  /**
   * Like {@link #connect(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<WebSocket> connect(String requestURI) {
    return connect(new WebSocketConnectOptions().setURI(requestURI));
  }

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
   * Update the client with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  default Future<Boolean> updateSSLOptions(SSLOptions options) {
    return updateSSLOptions(options, false);
  }

  /**
   * Like {@link #updateSSLOptions(SSLOptions)}  but supplying a handler that will be called when the update
   * happened (or has failed).
   *
   * @param options the new SSL options
   * @param handler the update handler
   */
  default void updateSSLOptions(SSLOptions options, Handler<AsyncResult<Boolean>> handler) {
    Future<Boolean> fut = updateSSLOptions(options);
    if (handler != null) {
      fut.onComplete(handler);
    }
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
  Future<Boolean> updateSSLOptions(SSLOptions options, boolean force);

  /**
   * Like {@link #updateSSLOptions(SSLOptions)}  but supplying a handler that will be called when the update
   * happened (or has failed).
   *
   * @param options the new SSL options
   * @param force force the update when options are equals
   * @param handler the update handler
   */
  default void updateSSLOptions(SSLOptions options, boolean force, Handler<AsyncResult<Boolean>> handler) {
    Future<Boolean> fut = updateSSLOptions(options, force);
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
