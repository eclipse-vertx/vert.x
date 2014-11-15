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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;

/**
 * An HTTP and WebSockets server<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * an event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances of HttpServer are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpServer extends Measured {

  /**
   * Return the request stream for the server. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this stream {@link io.vertx.core.streams.ReadStream#handler(io.vertx.core.Handler)}.
   *
   * @return the request stream
   */
  HttpServerRequestStream requestStream();

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer requestHandler(Handler<HttpServerRequest> handler);

  @GenIgnore
  Handler<HttpServerRequest> requestHandler();

  /**
   * Return the websocket stream for the server. If a websocket connect handshake is successful a
   * new {@link ServerWebSocket} instance will be created and passed to this stream {@link io.vertx.core.streams.ReadStream#handler(io.vertx.core.Handler)}.
   *
   * @return the websocket stream
   */
  ServerWebSocketStream websocketStream();

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link ServerWebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer websocketHandler(Handler<ServerWebSocket> handler);

  @GenIgnore
  Handler<ServerWebSocket> websocketHandler();

  @Fluent
  HttpServer listen();

  @Fluent
  HttpServer listen(Handler<AsyncResult<HttpServer>> listenHandler);

  /**
   * Close the server. Any open HTTP connections will be closed.
   */
  void close();

  /**
   * Close the server. Any open HTTP connections will be closed. The {@code completionHandler} will be called when the close
   * is complete.
   */
  void close(Handler<AsyncResult<Void>> completionHandler);

}
