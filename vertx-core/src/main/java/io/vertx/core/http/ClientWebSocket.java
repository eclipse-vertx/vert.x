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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

import java.util.List;

/**
 * Represents a client-side WebSocket.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface ClientWebSocket extends WebSocket {

  /**
   * Connect this WebSocket with the specified options.
   *
   * @param options  the request options
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> connect(WebSocketConnectOptions options);

  /**
   * Connect this WebSocket to the specified port, host and relative request URI.
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
   * Connect this WebSocket to the host and relative request URI and default port.
   *
   * @param host  the host
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  default Future<WebSocket> connect(String host, String requestURI) {
    return connect(new WebSocketConnectOptions().setHost(host).setURI(requestURI));
  }

  /**
   * Connect this WebSocket at the relative request URI using the default host and port.
   *
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  default Future<WebSocket> connect(String requestURI) {
    return connect(new WebSocketConnectOptions().setURI(requestURI));
  }

  @Override
  ClientWebSocket pause();

  @Override
  default ClientWebSocket resume() {
    return (ClientWebSocket) WebSocket.super.resume();
  }

  @Override
  ClientWebSocket fetch(long amount);

  @Override
  ClientWebSocket setWriteQueueMaxSize(int maxSize);

  @Override
  ClientWebSocket handler(Handler<Buffer> handler);

  @Override
  ClientWebSocket endHandler(Handler<Void> endHandler);

  @Override
  ClientWebSocket drainHandler(Handler<Void> handler);

  @Override
  ClientWebSocket closeHandler(Handler<Void> handler);

  @Override
  ClientWebSocket frameHandler(Handler<WebSocketFrame> handler);

  @Override
  ClientWebSocket textMessageHandler(@Nullable Handler<String> handler);

  @Override
  ClientWebSocket binaryMessageHandler(@Nullable Handler<Buffer> handler);

  @Override
  ClientWebSocket pongHandler(@Nullable Handler<Buffer> handler);

  @Override
  ClientWebSocket exceptionHandler(Handler<Throwable> handler);

}
