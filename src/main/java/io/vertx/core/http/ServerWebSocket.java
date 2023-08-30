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
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.HostAndPort;

import javax.net.ssl.SSLSession;

/**
 * Represents a server side WebSocket.
 * <p>
 * Instances of this class are passed into a {@link io.vertx.core.http.HttpServer#webSocketHandler} or provided
 * when a WebSocket handshake is manually {@link HttpServerRequest#toWebSocket}ed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface ServerWebSocket extends WebSocket {

  @Override
  ServerWebSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  ServerWebSocket handler(Handler<Buffer> handler);

  @Override
  ServerWebSocket pause();

  @Override
  ServerWebSocket resume();

  @Override
  ServerWebSocket fetch(long amount);

  @Override
  ServerWebSocket endHandler(Handler<Void> endHandler);

  @Override
  ServerWebSocket setWriteQueueMaxSize(int maxSize);

  @Override
  ServerWebSocket drainHandler(Handler<Void> handler);

  @Override
  ServerWebSocket closeHandler(Handler<Void> handler);

  @Override
  ServerWebSocket frameHandler(Handler<WebSocketFrame> handler);

  /**
   * @return the WebSocket handshake scheme
   */
  @Nullable
  String scheme();

  /**
   * @return the WebSocket handshake authority
   */
  @Nullable
  HostAndPort authority();

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
  @Nullable
  String query();

  /**
   * Accept the WebSocket and terminate the WebSocket handshake.
   * <p/>
   * This method should be called from the WebSocket handler to explicitly accept the WebSocket and
   * terminate the WebSocket handshake.
   *
   * @throws IllegalStateException when the WebSocket handshake is already set
   */
  void accept();

  /**
   * Reject the WebSocket.
   * <p>
   * Calling this method from the WebSocket handler when it is first passed to you gives you the opportunity to reject
   * the WebSocket, which will cause the WebSocket handshake to fail by returning
   * a {@literal 502} response code.
   * <p>
   * You might use this method, if for example you only want to accept WebSockets with a particular path.
   *
   * @throws IllegalStateException when the WebSocket handshake is already set
   */
  void reject();

  /**
   * Like {@link #reject()} but with a {@code status}.
   */
  void reject(int status);

  /**
   * Set an asynchronous result for the handshake, upon completion of the specified {@code future}, the
   * WebSocket will either be
   *
   * <ul>
   *   <li>accepted when the {@code future} succeeds with the HTTP {@literal 101} status code</li>
   *   <li>rejected when the {@code future} is succeeds with an HTTP status code different than {@literal 101}</li>
   *   <li>rejected when the {@code future} fails with the HTTP status code {@code 500}</li>
   * </ul>
   *
   * The provided future might be completed by the WebSocket itself, e.g calling the {@link #close()} method
   * will try to accept the handshake and close the WebSocket afterward. Thus it is advised to try to complete
   * the {@code future} with {@link Promise#tryComplete} or {@link Promise#tryFail}.
   * <p>
   * This method should be called from the WebSocket handler to explicitly set an asynchronous handshake.
   * <p>
   * Calling this method will override the {@code future} completion handler.
   *
   * @param future the future to complete with
   * @return a future notified when the handshake has completed
   * @throws IllegalStateException when the WebSocket has already an asynchronous result
   */
  Future<Integer> setHandshake(Future<Integer> future);

  /**
   * {@inheritDoc}
   *
   * <p>
   * The WebSocket handshake will be accepted when it hasn't yet been settled or when an asynchronous handshake
   * is in progress.
   */
  @Override
  Future<Void> close();

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see javax.net.ssl.SSLSession
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  SSLSession sslSession();

}
