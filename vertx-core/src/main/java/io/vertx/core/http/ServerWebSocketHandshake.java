/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
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
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;

/**
 * A server WebSocket handshake, allows to control acceptance or rejection of a WebSocket.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ServerWebSocketHandshake {

  /**
   *  Returns the HTTP headers.
   *
   * @return the headers
   */
  MultiMap headers();

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
  Future<ServerWebSocket> accept();

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
  default Future<Void> reject() {
    // SC_BAD_GATEWAY
    return reject(502);
  }

  /**
   * Like {@link #reject()} but with a {@code status}.
   */
  Future<Void> reject(int status);

  /**
   * @return the remote address for this connection, possibly {@code null} (e.g a server bound on a domain socket).
   * If {@code useProxyProtocol} is set to {@code true}, the address returned will be of the actual connecting client.
   */
  @CacheReturn
  SocketAddress remoteAddress();

  /**
   * @return the local address for this connection, possibly {@code null} (e.g a server bound on a domain socket)
   * If {@code useProxyProtocol} is set to {@code true}, the address returned will be of the proxy.
   */
  @CacheReturn
  SocketAddress localAddress();

  /**
   * @return true if this {@link io.vertx.core.http.HttpConnection} is encrypted via SSL/TLS.
   */
  boolean isSsl();

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see javax.net.ssl.SSLSession
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  SSLSession sslSession();

  /**
   * @return an ordered list of the peer certificates. Returns null if connection is
   *         not SSL.
   * @throws javax.net.ssl.SSLPeerUnverifiedException SSL peer's identity has not been verified.
   * @see SSLSession#getPeerCertificates() ()
   * @see #sslSession()
   */
  @GenIgnore()
  List<Certificate> peerCertificates() throws SSLPeerUnverifiedException;

}
