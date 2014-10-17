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
 * Represents a server side WebSocket that is passed into a the websocketHandler of an {@link HttpServer}<p>
 * Instances of this class are not thread-safe<p>
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
   * The uri the websocket handshake occurred at
   */
  String uri();

  /**
   * The path the websocket is attempting to connect at
   */
  String path();

  /**
   * The query string passed on the websocket uri
   */
  String query();

  /**
   * A map of all headers in the request to upgrade to websocket
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Reject the WebSocket<p>
   * Calling this method from the websocketHandler gives you the opportunity to reject
   * the websocket, which will cause the websocket handshake to fail by returning
   * a 404 response code.<p>
   * You might use this method, if for example you only want to accept websockets
   * with a particular path.
   */
  @Fluent
  ServerWebSocket reject();
}
