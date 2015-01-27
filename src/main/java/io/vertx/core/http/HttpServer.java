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
 * An HTTP and WebSockets server.
 * <p>
 * You receive HTTP requests by providing a {@link #requestHandler}. As requests arrive on the server the handler
 * will be called with the requests.
 * <p>
 * You receive WebSockets by providing a {@link #websocketHandler}. As WebSocket connections arrive on the server, the
 * WebSocket is passed to the handler.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpServer extends Measured {

  /**
   * Return the request stream for the server. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to the stream {@link io.vertx.core.streams.ReadStream#handler(io.vertx.core.Handler)}.
   *
   * @return the request stream
   */
  HttpServerRequestStream requestStream();

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so the API can be used fluently
   */
  HttpServer requestHandler(Handler<HttpServerRequest> handler);

  /**
   * @return  the request handler
   */
  @GenIgnore
  Handler<HttpServerRequest> requestHandler();

  /**
   * Return the websocket stream for the server. If a websocket connect handshake is successful a
   * new {@link ServerWebSocket} instance will be created and passed to the stream {@link io.vertx.core.streams.ReadStream#handler(io.vertx.core.Handler)}.
   *
   * @return the websocket stream
   */
  ServerWebSocketStream websocketStream();

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link ServerWebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so the API can be used fluently
   */
  HttpServer websocketHandler(Handler<ServerWebSocket> handler);

  /**
   * @return the websocketHandler
   */
  @GenIgnore
  Handler<ServerWebSocket> websocketHandler();

  /**
   * Tell the server to start listening. The server will listen on the port and host specified in the
   * {@link io.vertx.core.http.HttpServerOptions} that was used when creating the server.
   * <p>
   * The listen happens asynchronously and the server may not be listening until some time after the call has returned.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer listen();

  /**
   * Tell the server to start listening. The server will listen on the port and host specified here,
   * ignoring any value set in the {@link io.vertx.core.http.HttpServerOptions} that was used when creating the server.
   * <p>
   * The listen happens asynchronously and the server may not be listening until some time after the call has returned.
   *
   * @param port  the port to listen on
   * @param host  the host to listen on
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer listen(int port, String host);

  /**
   * Like {@link #listen(int, String)} but supplying a handler that will be called when the server is actually
   * listening (or has failed).
   *
   * @param port  the port to listen on
   * @param host  the host to listen on
   * @param listenHandler  the listen handler
   */
  @Fluent
  HttpServer listen(int port, String host, Handler<AsyncResult<HttpServer>> listenHandler);

  /**
   * Like {@link #listen(int, String)} but the server will listen on host "0.0.0.0" and port specified here ignoring
   * any value in the {@link io.vertx.core.http.HttpServerOptions} that was used when creating the server.
   *
   * @param port  the port to listen on
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer listen(int port);

  /**
   * Like {@link #listen(int)} but supplying a handler that will be called when the server is actually listening (or has failed).
   *
   * @param port  the port to listen on
   * @param listenHandler  the listen handler
   */
  @Fluent
  HttpServer listen(int port, Handler<AsyncResult<HttpServer>> listenHandler);

  /**
   * Like {@link #listen} but supplying a handler that will be called when the server is actually listening (or has failed).
   *
   * @param listenHandler  the listen handler
   */
  @Fluent
  HttpServer listen(Handler<AsyncResult<HttpServer>> listenHandler);

  /**
   * Close the server. Any open HTTP connections will be closed.
   * <p>
   * The close happens asynchronously and the server may not be closed until some time after the call has returned.
   */
  void close();

  /**
   * Like {@link #close} but supplying a handler that will be called when the server is actually closed (or has failed).
   *
   * @param completionHandler  the handler
   */
  void close(Handler<AsyncResult<Void>> completionHandler);

}
