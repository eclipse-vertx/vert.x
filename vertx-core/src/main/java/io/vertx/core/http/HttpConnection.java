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

import io.vertx.codegen.annotations.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Represents an HTTP connection.
 * <p/>
 * HTTP/1.x connection provides a limited implementation, the following methods are implemented:
 * <ul>
 *   <li>{@link #close}</li>
 *   <li>{@link #closeHandler}</li>
 *   <li>{@link #exceptionHandler}</li>
 * </ul>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpConnection {

  /**
   * @return the current connection window size or {@code -1} for HTTP/1.x
   */
  default int getWindowSize() {
    return -1;
  }

  /**
   * Update the current connection wide window size to a new size.
   * <p/>
   * Increasing this value, gives better performance when several data streams are multiplexed
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param windowSize the new window size
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default HttpConnection setWindowSize(int windowSize) {
    return this;
  }

  /**
   * Like {@link #goAway(long, int)} with a last stream id {@code -1} which means to disallow any new stream creation.
   */
  @Fluent
  default HttpConnection goAway(long errorCode) {
    return goAway(errorCode, -1);
  }

  /**
   * Like {@link #goAway(long, int, Buffer)} with no buffer.
   */
  @Fluent
  default HttpConnection goAway(long errorCode, int lastStreamId) {
    return goAway(errorCode, lastStreamId, null);
  }

  /**
   * Send a go away frame to the remote endpoint of the connection.
   * <p/>
   * <ul>
   *   <li>a {@literal GOAWAY} frame is sent to the to the remote endpoint with the {@code errorCode} and {@code debugData}</li>
   *   <li>any stream created after the stream identified by {@code lastStreamId} will be closed</li>
   *   <li>for an {@literal errorCode} is different than {@code 0} when all the remaining streams are closed this connection will be closed automatically</li>
   * </ul>
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param errorCode the {@literal GOAWAY} error code
   * @param lastStreamId the last stream id
   * @param debugData additional debug data sent to the remote endpoint
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData);

  /**
   * Set an handler called when a {@literal GOAWAY} frame is received.
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler);

  /**
   * Set a {@code handler} notified when the HTTP connection is shutdown: the client or server will close the connection
   * within a certain amount of time. This gives the opportunity to the {@code handler} to close the current requests in progress
   * gracefully before the HTTP connection is forcefully closed.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection shutdownHandler(@Nullable  Handler<Void> handler);

  /**
   * Shutdown with a 30 seconds timeout ({@code shutdown(30, TimeUnit.SECONDS)}).
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(30, TimeUnit.SECONDS);
  }

  /**
   * Initiate a graceful connection shutdown, the connection is taken out of service and closed when all the inflight requests
   * are processed, otherwise after a {@code timeout} the connection will be closed. Client connection are immediately removed
   * from the pool.
   *
   * <ul>
   *   <li>HTTP/2 connections will send a go away frame immediately to signal the other side the connection will close.</li>
   *   <li>HTTP/1.x connection will be closed.</li>
   * </ul>
   *
   * @param timeout the amount of time after which all resources are forcibly closed
   * @param unit the of the timeout
   * @return a future completed when shutdown has completed
   */
  Future<Void> shutdown(long timeout, TimeUnit unit);

  /**
   * Set a close handler. The handler will get notified when the connection is closed.
   *
   * @param handler the handler to be notified
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection closeHandler(Handler<Void> handler);

  /**
   * Close immediately ({@code shutdown(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return shutdown(0, TimeUnit.SECONDS);
  }

  /**
   * @return the latest server settings acknowledged by the remote endpoint - this is not implemented for HTTP/1.x
   */
  @Deprecated
  default Http2Settings settings() {
    return (Http2Settings) httpSettings();
  }

  /**
   * @return the latest server settings acknowledged by the remote endpoint
   */
  HttpSettings httpSettings();

  /**
   * Send to the remote endpoint an update of this endpoint settings
   * <p/>
   * The {@code completionHandler} will be notified when the remote endpoint has acknowledged the settings.
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param settings the new settings
   * @return a future completed when the settings have been acknowledged by the remote endpoint
   */
  @Deprecated
  default Future<Void> updateSettings(Http2Settings settings) {
    return updateHttpSettings(settings);
  }

  /**
   * Send to the remote endpoint an update of this endpoint settings
   * <p/>
   * The {@code completionHandler} will be notified when the remote endpoint has acknowledged the settings.
   * <p/>
   *
   * @param settings the new settings
   * @return a future completed when the settings have been acknowledged by the remote endpoint
   */
  Future<Void> updateHttpSettings(HttpSettings settings);

  /**
   * @return the current remote endpoint settings for this connection - this is not implemented for HTTP/1.x
   */
  @Deprecated
  default Http2Settings remoteSettings() {
    return (Http2Settings) remoteHttpSettings();
  }

  /**
   * @return the current remote endpoint settings for this connection
   */
  HttpSettings remoteHttpSettings();

  /**
   * Set an handler that is called when remote endpoint {@link Http2Settings} are updated.
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param handler the handler for remote endpoint settings
   * @return a reference to this, so the API can be used fluently
   */
  @Deprecated
  @Fluent
  default HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    return remoteHttpSettingsHandler(result -> handler.handle((Http2Settings) result));
  }

  /**
   * Set an handler that is called when remote endpoint {@link HttpSettings} are updated.
   * <p/>
   * @param handler the handler for remote endpoint settings
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection remoteHttpSettingsHandler(Handler<HttpSettings> handler);

  /**
   * Send a {@literal PING} frame to the remote endpoint.
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param data the 8 bytes data of the frame
   * @return a future notified with the pong reply or the failure
   */
  Future<Buffer> ping(Buffer data);

  /**
   * Set an handler notified when a {@literal PING} frame is received from the remote endpoint.
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param handler the handler to be called when a {@literal PING} is received
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection pingHandler(@Nullable Handler<Buffer> handler);

  /**
   * Set an handler called when a connection error happens
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection exceptionHandler(Handler<Throwable> handler);

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

  /**
   * Returns the SNI server name presented during the SSL handshake by the client.
   *
   * @return the indicated server name
   */
  String indicatedServerName();

}
