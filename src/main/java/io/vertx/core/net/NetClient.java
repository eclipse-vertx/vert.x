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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;

/**
 * A TCP client.
 * <p>
 * Multiple connections to different servers can be made using the same instance.
 * <p>
 * This client supports a configurable number of connection attempts and a configurable
 * delay between attempts.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface NetClient extends Measured {

  /**
   * Open a connection to a server at the specific {@code port} and {@code host}.
   * <p>
   * {@code host} can be a valid host name or IP address. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   *
   * @param port  the port
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler);

  /**
   * Open a connection to a server at the specific {@code port} and {@code host}.
   * <p>
   * {@code host} can be a valid host name or IP address. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   *
   * @param port the port
   * @param host the host
   * @param serverName the SNI server name
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetClient connect(int port, String host, String serverName, Handler<AsyncResult<NetSocket>> connectHandler);

  /**
   * Open a connection to a server at the specific {@code remoteAddress}.
   * <p>
   * The connect is done asynchronously and on success, a {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   *
   * @param remoteAddress the remote address
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetClient connect(SocketAddress remoteAddress, Handler<AsyncResult<NetSocket>> connectHandler);

  /**
   * Open a connection to a server at the specific {@code remoteAddress}.
   * <p>
   * The connect is done asynchronously and on success, a {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   *
   * @param remoteAddress the remote address
   * @param serverName the SNI server name
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetClient connect(SocketAddress remoteAddress, String serverName, Handler<AsyncResult<NetSocket>> connectHandler);

  /**
   * Close the client.
   * <p>
   * Any sockets which have not been closed manually will be closed here. The close is asynchronous and may not
   * complete until some time after the method has returned.
   */
  void close();

}
