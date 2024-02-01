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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.impl.SocketAddressImpl;
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
   * @deprecated instead use {@link #connectHandler(Handler)}
   */
  @Deprecated
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
   * @return a future completed with the listen operation result
   */
  Future<NetServer> listen();

  /**
   * Like {@link #listen} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param listenHandler  handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default NetServer listen(Handler<AsyncResult<NetServer>> listenHandler) {
    Future<NetServer> fut = listen();
    if (listenHandler != null) {
      fut.onComplete(listenHandler);
    }
    return this;
  }

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
   * @return a future completed with the listen operation result
   */
  default Future<NetServer> listen(int port, String host) {

    return listen(new SocketAddressImpl(port, host));
  }

  /**
   * Like {@link #listen(int, String)} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param port  the port to listen on
   * @param host  the host to listen on
   * @param listenHandler handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default NetServer listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler) {
    Future<NetServer> fut = listen(port, host);
    if (listenHandler != null) {
      fut.onComplete(listenHandler);
    }
    return this;
  }

  /**
   * Start listening on the specified port and host "0.0.0.0", ignoring port and host configured in the
   * {@link io.vertx.core.net.NetServerOptions} used when creating the server.
   * <p>
   * Port {@code 0} can be specified meaning "choose an random port".
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @return a future completed with the listen operation result
   */
  default Future<NetServer> listen(int port) {
    return listen(port, "0.0.0.0");
  }

  /**
   * Like {@link #listen(int)} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param port  the port to listen on
   * @param listenHandler handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(port, "0.0.0.0", listenHandler);
  }

  /**
   * Start listening on the specified local address, ignoring port and host configured in the {@link io.vertx.core.net.NetServerOptions} used when
   * creating the server.
   * <p>
   * The server may not be listening until some time after the call to listen has returned.
   *
   * @param localAddress the local address to listen on
   * @return a future completed with the listen operation result
   */
  Future<NetServer> listen(SocketAddress localAddress);

  /**
   * Like {@link #listen(SocketAddress)} but providing a handler that will be notified when the server is listening, or fails.
   *
   * @param localAddress the local address to listen on
   * @param listenHandler handler that will be notified when listening or failed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default NetServer listen(SocketAddress localAddress, Handler<AsyncResult<NetServer>> listenHandler) {
    Future<NetServer> fut = listen(localAddress);
    if (listenHandler != null) {
      fut.onComplete(listenHandler);
    }
    return this;
  }

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
   *
   * @return a future completed with the listen operation result
   */
  Future<Void> close();

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

  /**
   * <p>Update the server with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The boolean succeeded future result indicates whether the update occurred.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  default Future<Boolean> updateSSLOptions(SSLOptions options) {
    return updateSSLOptions(options, false);
  }

  /**
   * Like {@link #updateSSLOptions(SSLOptions)}  but supplying a handler that will be called when the update
   * happened (or has failed).
   *
   * @param options the new SSL options
   * @param handler the update handler
   */
  default void updateSSLOptions(SSLOptions options, Handler<AsyncResult<Boolean>> handler) {
    Future<Boolean> fut = updateSSLOptions(options);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

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
  Future<Boolean> updateSSLOptions(SSLOptions options, boolean force);

  /**
   * Like {@link #updateSSLOptions(SSLOptions)}  but supplying a handler that will be called when the update
   * happened (or has failed).
   *
   * @param options the new SSL options
   * @param force force the update when options are equals
   * @param handler the update handler
   */
  default void updateSSLOptions(SSLOptions options, boolean force, Handler<AsyncResult<Boolean>> handler) {
    Future<Boolean> fut = updateSSLOptions(options, force);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  /**
   * Update traffic shaping options {@code options}, the update happens if valid values are passed for traffic
   * shaping options. This update happens synchronously and at best effort for rate update to take effect immediately.
   *
   * @param options the new traffic shaping options
   */
  void updateTrafficShapingOptions(TrafficShapingOptions options);
}
