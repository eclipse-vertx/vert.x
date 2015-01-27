/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.http;

import io.vertx.core.MultiMap;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;

/**
 * Represents a server side WebSocket.
 * <p>
 * Instances of this class are passed into a {@link io.vertx.core.http.HttpServer#websocketHandler} or provided
 * when a WebSocket handshake is manually {@link HttpServerRequest#upgrade}ed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface ServerWebSocket extends WebSocketBase {

  @Override
  ServerWebSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  ServerWebSocket handler(Handler<Buffer> handler);

  @Override
  ServerWebSocket pause();

  @Override
  ServerWebSocket resume();

  @Override
  ServerWebSocket endHandler(Handler<Void> endHandler);

  @Override
  ServerWebSocket write(Buffer data);

  @Override
  ServerWebSocket setWriteQueueMaxSize(int maxSize);

  @Override
  ServerWebSocket drainHandler(Handler<Void> handler);

  @Override
  ServerWebSocket writeFrame(WebSocketFrame frame);

  @Override
  ServerWebSocket writeMessage(Buffer data);

  @Override
  ServerWebSocket closeHandler(Handler<Void> handler);

  @Override
  ServerWebSocket frameHandler(Handler<WebSocketFrame> handler);

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
  String query();

  /**
   * @return the headers in the WebSocket handshake
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Reject the WebSocket.
   * <p>
   * Calling this method from the websocket handler when it is first passed to you gives you the opportunity to reject
   * the websocket, which will cause the websocket handshake to fail by returning
   * a 404 response code.
   * <p>
   * You might use this method, if for example you only want to accept WebSockets with a particular path.
   */
  void reject();
}
