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

package io.vertx.core.http;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.HostAndPort;

import javax.net.ssl.SSLSession;

/**
 * Represents a server side WebSocket.
 * <p>
 * Instances of this class are passed into a {@link io.vertx.core.http.HttpServer#webSocketHandler} or provided
 * when a WebSocket handshake is manually {@link HttpServerRequest#toWebSocket}ed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface ServerWebSocket extends WebSocket {

  @Override
  ServerWebSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  ServerWebSocket handler(Handler<Buffer> handler);

  @Override
  ServerWebSocket pause();

  @Override
  default ServerWebSocket resume() {
    return (ServerWebSocket) WebSocket.super.resume();
  }

  @Override
  ServerWebSocket fetch(long amount);

  @Override
  ServerWebSocket endHandler(Handler<Void> endHandler);

  @Override
  ServerWebSocket setWriteQueueMaxSize(int maxSize);

  @Override
  ServerWebSocket drainHandler(Handler<Void> handler);

  @Override
  ServerWebSocket closeHandler(Handler<Void> handler);

  @Override
  ServerWebSocket frameHandler(Handler<WebSocketFrame> handler);

  /**
   * @return the WebSocket handshake scheme
   */
  @Nullable
  String scheme();

  /**
   * @return the WebSocket handshake authority
   */
  @Nullable
  HostAndPort authority();

  /*
   * @return the WebSocket handshake URI. This is a relative URI.
   */
  String uri();

  /**
   * @return the WebSocket handshake path.
   */
  String path();

  /**
   * @return the WebSocket handshake query string.
   */
  @Nullable
  String query();

  /**
   * {@inheritDoc}
   *
   * <p>
   * The WebSocket handshake will be accepted when it hasn't yet been settled or when an asynchronous handshake
   * is in progress.
   */
  @Override
  default Future<Void> close() {
    return WebSocket.super.close();
  }

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see javax.net.ssl.SSLSession
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  SSLSession sslSession();

}
