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

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
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
public interface NetSocket extends Socket {

  @Override
  NetSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  NetSocket handler(Handler<Buffer> handler);

  @Override
  NetSocket pause();

  @Override
  NetSocket resume();

  @Override
  NetSocket fetch(long amount);

  /**
   * {@inheritDoc}
   * <p>
   * This handler might be called after the close handler when the socket is paused and there are still
   * buffers to deliver.
   */
  @Override
  NetSocket endHandler(Handler<Void> endHandler);

  @Override
  NetSocket setWriteQueueMaxSize(int maxSize);

  @Override
  NetSocket drainHandler(Handler<Void> handler);

  /**
   * When a {@code NetSocket} is created, it may register an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.
   * <p>
   * By default, no handler is registered, the feature must be enabled via {@link NetClientOptions#setRegisterWriteHandler(boolean)} or {@link NetServerOptions#setRegisterWriteHandler(boolean)}.
   * <p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other connections which are owned by different event loops.
   *
   * @return the write handler ID
   * @see NetClientOptions#setRegisterWriteHandler(boolean)
   * @see NetServerOptions#setRegisterWriteHandler(boolean)
   */
  String writeHandlerID();

  /**
   * Write a {@link String} to the connection, encoded in UTF-8.
   *
   * @param str  the string to write
   * @return a future result of the write
   */
  Future<Void> write(String str);

  /**
   * Write a {@link String} to the connection, encoded using the encoding {@code enc}.
   *
   * @param str  the string to write
   * @param enc  the encoding to use
   * @return a future completed with the result
   */
  Future<Void> write(String str, String enc);

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @return a future result of the send operation
   */
  default Future<Void> sendFile(String filename) {
    return sendFile(filename, 0, Long.MAX_VALUE);
  }

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @param offset offset
   * @return a future result of the send operation
   */
  default Future<Void> sendFile(String filename, long offset) {
    return sendFile(filename, offset, Long.MAX_VALUE);
  }

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @param offset offset
   * @param length length
   * @return a future result of the send operation
   */
  Future<Void> sendFile(String filename, long offset, long length);

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
   * Calls {@link #close()}
   *
   * @return a future completed with the result
   */
  @Override
  Future<Void> end();

  /**
   * Close the socket
   *
   * @return a future completed with the result
   */
  Future<Void> close();

  /**
   * Set a {@code handler} notified when the socket is closed
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket closeHandler(@Nullable Handler<Void> handler);

  /**
   * Set a {@code handler} notified when the socket is shutdown: the client or server will close the connection
   * within a certain amount of time. This gives the opportunity to the {@code handler} to close the socket gracefully before
   * the socket is closed.
   *
   * @param handler  the handler notified
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket shutdownHandler(@Nullable Handler<Void> handler);

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
   * @return true if this {@link io.vertx.core.net.NetSocket} is encrypted via SSL/TLS.
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
   * @see javax.net.ssl.SSLSession#getPeerCertificates()
   * @see #sslSession()
   */
  @GenIgnore()
  List<Certificate> peerCertificates() throws SSLPeerUnverifiedException;

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

