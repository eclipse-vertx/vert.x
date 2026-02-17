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

package io.vertx.core.net;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.impl.SocketAddressImpl;

import java.time.Duration;

/**
 * Represents a TCP server
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface TcpServer extends Measured {

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link TcpSocket} and passes it to the
   * connect handler.
   *
   * @return a reference to this, so the API can be used fluently
   */
  TcpServer connectHandler(@Nullable Handler<TcpSocket> handler);

  /**
   * Start listening on the port and host as configured in the {@link NetServerOptions} used when
   * creating the server.
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a future completed with the listen operation result
   */
  Future<SocketAddress> listen();

  /**
   * Start listening on the specified port and host, ignoring port and host configured in the {@link NetServerOptions} used when
   * creating the server.
   * <p>
   * Port {@code 0} can be specified meaning "choose an random port".
   * <p>
   * Host {@code 0.0.0.0} can be specified meaning "listen on all available interfaces".
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a future completed with the listen operation result
   */
  default Future<SocketAddress> listen(int port, String host) {
    return listen(new SocketAddressImpl(port, host));
  }

  /**
   * Start listening on the specified port and host "0.0.0.0", ignoring port and host configured in the
   * {@link NetServerOptions} used when creating the server.
   * <p>
   * Port {@code 0} can be specified meaning "choose an random port".
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a future completed with the listen operation result
   */
  default Future<SocketAddress> listen(int port) {
    return listen(port, "0.0.0.0");
  }

  /**
   * Start listening on the specified local address, ignoring port and host configured in the {@link NetServerOptions} used when
   * creating the server.
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @param localAddress the local address to listen on
   * @return a future completed with the listen operation result
   */
  Future<SocketAddress> listen(SocketAddress localAddress);

  /**
   * Set an exception handler called for socket errors happening before the connection
   * is passed to the {@link #connectHandler}, e.g during the TLS handshake.
   *
   * @param handler the handler to set
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Fluent
  TcpServer exceptionHandler(Handler<Throwable> handler);

  /**
   * Close the server. This will close any currently open connections. The close may not complete until after this
   * method has returned.
   *
   * @return a future completed with the listen operation result
   */
  default Future<Void> close() {
    return shutdown(Duration.ZERO);
  }

  /**
   * Shutdown with a 30 seconds timeout ({@code shutdown(30, TimeUnit.SECONDS)}).
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(Duration.ofSeconds(30));
  }


  /**
   * Initiate the server shutdown sequence.
   * <p>
   * Connections are taken out of service and notified the close sequence has started through {@link TcpSocket#shutdownHandler(Handler)}.
   * When all connections are closed the client is closed. When the {@code timeout} expires, all unclosed connections are immediately closed.
   *
   * @return a future notified when the client is closed
   * @param timeout the amount of time after which all resources are forcibly closed
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> shutdown(Duration timeout);

  /**
   * <p>Update the server with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The boolean succeeded future result indicates whether the update occurred.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  default Future<Boolean> updateSSLOptions(ServerSSLOptions options) {
    return updateSSLOptions(options, false);
  }

  /**
   * The socket address the server is listening on. This is useful if you bound the server specifying 0 as port number
   * signifying an ephemeral port
   *
   * @return the server bind address the server is listening on or {@code null}
   */
  SocketAddress bindAddress();

  /**
   * <p>Update the server with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The {@code options} object is compared using its {@code equals} method against the existing options to prevent
   * an update when the objects are equals since loading options can be costly, this can happen for share TCP servers.
   * When object are equals, setting {@code force} to {@code true} forces the update.
   *
   * <p>The boolean succeeded future result indicates whether the update occurred.
   *
   * @param options the new SSL options
   * @param force force the update when options are equals
   * @return a future signaling the update success
   */
  Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force);

  /**
   * <p>Update the server with new traffic {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The {@code options} object is compared using its {@code equals} method against the existing options to prevent
   * an update when the objects are equals since loading options can be costly, this can happen for share TCP servers.
   * When object are equals, setting {@code force} to {@code true} forces the update.
   *
   * <p>The boolean succeeded future result indicates whether the update occurred.
   *
   * @param options the new traffic shaping options
   * @return a future signaling the update success
   */
  Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options);
}
