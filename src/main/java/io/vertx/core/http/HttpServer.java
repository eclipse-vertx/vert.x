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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;

/**
 * An HTTP and WebSockets server.
 * <p>
 * You receive HTTP requests by providing a {@link #requestHandler}. As requests arrive on the server the handler
 * will be called with the requests.
 * <p>
 * You receive WebSockets by providing a {@link #webSocketHandler}. As WebSocket connections arrive on the server, the
 * WebSocket is passed to the handler.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpServer extends Measured {

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer requestHandler(Handler<HttpServerRequest> handler);

  /**
   * @return  the request handler
   */
  @GenIgnore
  Handler<HttpServerRequest> requestHandler();

  /**
   * Set a {@code handler} for handling invalid requests. When an invalid request is received by the server
   * this handler will be called with the request. The handler can send any HTTP response, when the response
   * ends, the server shall close the connection. {@link HttpServerRequest#decoderResult()} can be used
   * to obtain the Netty decoder result and the failure cause reported by the decoder.
   *
   * <p> Currently this handler is only used for HTTP/1.x requests.
   *
   * <p> When no specific handler is set, the {@link HttpServerRequest#DEFAULT_INVALID_REQUEST_HANDLER} is used.
   *
   * @return a reference to this, so the API can be used fluently
   */
  HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler);

  /**
   * Set a connection handler for the server.
   * <br/>
   * The handler will always be called on the event-loop thread.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer connectionHandler(Handler<HttpConnection> handler);

  /**
   * Set an exception handler called for socket errors happening before the HTTP connection
   * is established, e.g during the TLS handshake.
   *
   * @param handler the handler to set
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer exceptionHandler(Handler<Throwable> handler);

  /**
   * Set the WebSocket handler for the server to {@code wsHandler}. If a WebSocket connect handshake is successful a
   * new {@link ServerWebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServer webSocketHandler(Handler<ServerWebSocket> handler);

  /**
   * @return the WebSocket handler
   */
  @GenIgnore
  Handler<ServerWebSocket> webSocketHandler();

  /**
   * Update the server SSL options.
   *
   * Update only happens if the SSL options is valid.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  Future<Void> updateSSLOptions(SSLOptions options);

  /**
   * Tell the server to start listening. The server will listen on the port and host specified in the
   * {@link io.vertx.core.http.HttpServerOptions} that was used when creating the server.
   * <p>
   * The listen happens asynchronously and the server may not be listening until some time after the call has returned.
   *
   * @return a future completed with the listen operation result
   */
  Future<HttpServer> listen();

  /**
   * Tell the server to start listening. The server will listen on the port and host specified here,
   * ignoring any value set in the {@link io.vertx.core.http.HttpServerOptions} that was used when creating the server.
   * <p>
   * The listen happens asynchronously and the server may not be listening until some time after the call has returned.
   *
   * @param port  the port to listen on
   * @param host  the host to listen on
   *
   * @return a future completed with the listen operation result
   */
  default Future<HttpServer> listen(int port, String host) {
    return listen(new SocketAddressImpl(port, host));
  }

  /**
   * Tell the server to start listening on the given address supplying
   * a handler that will be called when the server is actually
   * listening (or has failed).
   *
   * @param address the address to listen on
   * @return a future completed with the listen operation result
   */
  Future<HttpServer> listen(SocketAddress address);

  /**
   * Like {@link #listen(int, String)} but the server will listen on host "0.0.0.0" and port specified here ignoring
   * any value in the {@link io.vertx.core.http.HttpServerOptions} that was used when creating the server.
   *
   * @param port  the port to listen on
   *
   * @return a future completed with the listen operation result
   */
  default Future<HttpServer> listen(int port) {
    return listen(port, "0.0.0.0");
  }

  /**
   * Close the server. Any open HTTP connections will be closed.
   * <p>
   * The close happens asynchronously and the server may not be closed until some time after the call has returned.
   *
   * @return a future completed with the result
   */
  Future<Void> close();

  /**
   * The actual port the server is listening on. This is useful if you bound the server specifying 0 as port number
   * signifying an ephemeral port
   *
   * @return the actual port the server is listening on.
   */
  int actualPort();
}
