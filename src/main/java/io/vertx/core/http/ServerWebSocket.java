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

package io.vertx.core.http;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

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
  ServerWebSocket fetch(long amount);

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
  ServerWebSocket writeFinalTextFrame(String text);

  @Override
  ServerWebSocket writeFinalBinaryFrame(Buffer data);

  @Override
  ServerWebSocket writeBinaryMessage(Buffer data);

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
  @Nullable
  String query();

  /**
   * @return the headers in the WebSocket handshake
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Accept the WebSocket and terminate the WebSocket handshake.
   * <p/>
   * This method should be called from the websocket handler to explicitely accept the websocker and
   * terminate the WebSocket handshake.
   */
  void accept();

  /**
   * Reject the WebSocket.
   * <p>
   * Calling this method from the websocket handler when it is first passed to you gives you the opportunity to reject
   * the websocket, which will cause the websocket handshake to fail by returning
   * a {@literal 502} response code.
   * <p>
   * You might use this method, if for example you only want to accept WebSockets with a particular path.
   */
  void reject();

  /**
   * Like {@link #reject()} but with a {@code status}.
   */
  void reject(int status);

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see javax.net.ssl.SSLSession
   */
  @SuppressWarnings("codegen-allow-any-java-type")
  SSLSession sslSession();

  /**
   * Note: Java SE 5+ recommends to use javax.net.ssl.SSLSession#getPeerCertificates() instead of
   * of javax.net.ssl.SSLSession#getPeerCertificateChain() which this method is based on. Use {@link #sslSession()} to
   * access that method.
   *
   * @return an ordered array of the peer certificates. Returns null if connection is
   *         not SSL.
   * @throws javax.net.ssl.SSLPeerUnverifiedException SSL peer's identity has not been verified.
   * @see javax.net.ssl.SSLSession#getPeerCertificateChain()
   * @see #sslSession()
   */
  @GenIgnore
  X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException;
}
