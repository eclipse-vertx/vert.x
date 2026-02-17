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

import io.vertx.codegen.annotations.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a socket-like interface to a TCP connection on either the
 * client or the server side.
 * <p>
 * Instances of this class are created on the client side by an {@link NetClient}
 * when a connection to a server is made, or on the server side by a {@link NetServer}
 * when a server accepts a connection.
 * <p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface TcpSocket extends Socket {

  @Override
  TcpSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  TcpSocket handler(Handler<Buffer> handler);

  @Override
  TcpSocket pause();

  @Override
  TcpSocket resume();

  @Override
  TcpSocket fetch(long amount);

  /**
   * {@inheritDoc}
   * <p>
   * This handler might be called after the close handler when the socket is paused and there are still
   * buffers to deliver.
   */
  @Override
  TcpSocket endHandler(Handler<Void> endHandler);

  @Override
  TcpSocket setWriteQueueMaxSize(int maxSize);

  @Override
  TcpSocket drainHandler(Handler<Void> handler);

  /**
   * @return the remote address for this connection, possibly {@code null} (e.g a server bound on a domain socket).
   * If {@code useProxyProtocol} is set to {@code true}, the address returned will be of the actual connecting client.
   */
  @CacheReturn
  SocketAddress remoteAddress();

  /**
   * Like {@link #remoteAddress()} but returns the proxy remote address when {@code real} is {@code true}
   */
  SocketAddress remoteAddress(boolean real);

  /**
   * @return the local address for this connection, possibly {@code null} (e.g a server bound on a domain socket)
   * If {@code useProxyProtocol} is set to {@code true}, the address returned will be of the proxy.
   */
  @CacheReturn
  SocketAddress localAddress();

  /**
   * Like {@link #localAddress()} ()} but returns the server local address when {@code real} is {@code true}
   */
  SocketAddress localAddress(boolean real);

  /**
   * Like {@link #upgradeToSsl(SSLOptions, String, Buffer)} with the default SSL options, without indicating a server name,
   * without an upgrade message.
   */
  default Future<Void> upgradeToSsl() {
    return upgradeToSsl(null, null, null);
  }

  /**
   * Like {@link #upgradeToSsl(SSLOptions, String, Buffer)} with the default SSL options and without indicating a server name.
   */
  default Future<Void> upgradeToSsl(Buffer msg) {
    return upgradeToSsl(null, null, msg);
  }

  /**
   * Like {@link #upgradeToSsl(SSLOptions, String, Buffer)} with the default SSL options and without an update message.
   */
  default Future<Void> upgradeToSsl(String serverName) {
    return upgradeToSsl(null, serverName, null);
  }

  /**
   * Like {@link #upgradeToSsl(SSLOptions, String, Buffer)} with the default SSL options.
   */
  default Future<Void> upgradeToSsl(String serverName, Buffer msg) {
    return upgradeToSsl(null, serverName, msg);
  }

  /**
   * Like {@link #upgradeToSsl(SSLOptions, String, Buffer)} without an upgrade message.
   */
  default Future<Void> upgradeToSsl(SSLOptions sslOptions, String serverName) {
    return upgradeToSsl(sslOptions, serverName, null);
  }

  /**
   * Like {@link #upgradeToSsl(SSLOptions, String, Buffer)} without indicating a server name
   */
  default Future<Void> upgradeToSsl(SSLOptions sslOptions, Buffer msg) {
    return upgradeToSsl(sslOptions, null, msg);
  }

  /**
   * <p>Upgrade the channel to use SSL/TLS, in other words proceeds to the TLS handshake.</p>
   *
   * <p>The {@code upgrade} message will be sent after the socket is ready to proceed to the TLS handshake in
   * order to avoid data races. In practice is usually send by a server when it sends a message to the client
   * to proceed to the handshake, e.g. {@code 250 STARTTLS} for an SMTP server, it should
   * be {@code null} on a client</p>
   *
   * <p>The server name is sent in the client handshake, it should be {@code null} on a server.</p>
   *
   * <p>Be aware that for this to work SSL must be configured.</p>
   *
   * @param sslOptions the SSL options
   * @param serverName the server name
   * @param upgrade the upgrade message to send
   * @return a future completed when the connection has been upgraded to SSL
   */
  Future<Void> upgradeToSsl(SSLOptions sslOptions, String serverName, Buffer upgrade);

  /**
   * Upgrade channel to use SSL/TLS. Be aware that for this to work SSL must be configured.
   *
   * @param sslOptions the SSL options
   * @return a future completed when the connection has been upgraded to SSL
   */
  default Future<Void> upgradeToSsl(SSLOptions sslOptions) {
    return upgradeToSsl(sslOptions, null, null);
  }

  /**
   * @return true if this {@link TcpSocket} is encrypted via SSL/TLS.
   */
  boolean isSsl();

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see SSLSession
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  SSLSession sslSession();

  /**
   * @return an ordered list of the peer certificates. Returns null if connection is
   *         not SSL.
   * @throws SSLPeerUnverifiedException SSL peer's identity has not been verified.
   * @see SSLSession#getPeerCertificates()
   * @see #sslSession()
   */
  @GenIgnore()
  default List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    SSLSession session = sslSession();
    if (session != null) {
      return Arrays.asList(session.getPeerCertificates());
    } else {
      return null;
    }
  }

  /**
   * Returns the SNI server name presented during the SSL handshake by the client.
   *
   * @return the indicated server name
   */
  String indicatedServerName();

  /**
   * @return the application-level protocol negotiated during the TLS handshake
   */
  String applicationLayerProtocol();

}

