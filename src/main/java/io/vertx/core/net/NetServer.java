/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;
import io.vertx.core.streams.ReadStream;

/**
 * Represents a TCP server
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface NetServer extends Measured {

  /**
   * Return the connect stream for this server. The server can only have at most one handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link NetSocket} and passes it to the
   * connect stream {@link ReadStream#handler(io.vertx.core.Handler)}.
   *
   * @return the connect stream
   */
  ReadStream<NetSocket> connectStream();

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link NetSocket} and passes it to the
   * connect handler.
   *
   * @return a reference to this, so the API can be used fluently
   */
  NetServer connectHandler(@Nullable Handler<NetSocket> handler);

  @GenIgnore
  Handler<NetSocket> connectHandler();

  /**
   * Start listening on the port and host as configured in the {@link io.vertx.core.net.NetServerOptions} used when
   * creating the server.
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen();

  /**
   * Like {@link #listen} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param listenHandler  handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Start listening on the specified port and host, ignoring port and host configured in the {@link io.vertx.core.net.NetServerOptions} used when
   * creating the server.
   * <p>
   * Port {@code 0} can be specified meaning "choose an random port".
   * <p>
   * Host {@code 0.0.0.0} can be specified meaning "listen on all available interfaces".
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(int port, String host);

  /**
   * Like {@link #listen(int, String)} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param port  the port to listen on
   * @param host  the host to listen on
   * @param listenHandler handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Start listening on the specified port and host "0.0.0.0", ignoring port and host configured in the
   * {@link io.vertx.core.net.NetServerOptions} used when creating the server.
   * <p>
   * Port {@code 0} can be specified meaning "choose an random port".
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(int port);

  /**
   * Like {@link #listen(int)} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param port  the port to listen on
   * @param listenHandler handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Start listening on the specified local address, ignoring port and host configured in the {@link io.vertx.core.net.NetServerOptions} used when
   * creating the server.
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @param localAddress the local address to listen on
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(SocketAddress localAddress);

  /**
   * Like {@link #listen(SocketAddress)} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param localAddress the local address to listen on
   * @param listenHandler handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetServer listen(SocketAddress localAddress, Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Set an exception handler called for socket errors happening before the connection
   * is passed to the {@link #connectHandler}, e.g during the TLS handshake.
   *
   * @param handler the handler to set
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Fluent
  NetServer exceptionHandler(Handler<Throwable> handler);

  /**
   * Close the server. This will close any currently open connections. The close may not complete until after this
   * method has returned.
   */
  void close();

  /**
   * Like {@link #close} but supplying a handler that will be notified when close is complete.
   *
   * @param completionHandler  the handler
   */
  void close(Handler<AsyncResult<Void>> completionHandler);

  /**
   * The actual port the server is listening on. This is useful if you bound the server specifying 0 as port number
   * signifying an ephemeral port
   *
   * @return the actual port the server is listening on.
   */
  int actualPort();
}
