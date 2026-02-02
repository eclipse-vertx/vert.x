/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

import javax.net.ssl.SSLSession;
import java.time.Duration;

/**
 * <p>A Quic connection between a client and a server, providing support for handling or creating Quic {@link QuicStream streams}</p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface QuicConnection {

  /**
   * Set a handler processing incoming Quic streams.
   *
   * @param handler the handler processing streams
   * @return this instance of a connection
   */
  QuicConnection handler(Handler<QuicStream> handler);

  /**
   * Set a {@code handler} notified when the connection is shutdown: the client or server will close the connection
   * within a certain amount of time. This gives the opportunity to the {@code handler} to close the connection gracefully before
   * the socket is closed.
   *
   * @param handler  the handler notified
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  QuicConnection shutdownHandler(Handler<Duration> handler);

  /**
   * Set a handler called when the connection is closed.
   *
   * @param handler the handler signaled with the connection close
   * @return this instance of a connection
   */
  QuicConnection closeHandler(Handler<Void> handler);

  /**
   * Open a bidirectional stream to the remote endpoint.
   *
   * @return a Quic stream as a future
   */
  default Future<QuicStream> openStream() {
    return openStream(true);
  }

  /**
   * Open a stream to the remote endpoint with {@code bidirectional parameter} specifying its directionality.
   *
   * @param bidirectional whether the stream is unidirectional or bidirectional
   * @return A Quic stream as a future
   */
  Future<QuicStream> openStream(boolean bidirectional);

  /**
   * Set a handler called with the datagram addressed to this connection.
   *
   * @param handler the handler
   * @return this instance of a connection
   */
  QuicConnection datagramHandler(Handler<Buffer> handler);

  /**
   * Send a datagram.
   *
   * @param buffer the datagram
   * @return a future signaling when the datagram has been written
   */
  Future<Void> writeDatagram(Buffer buffer);

  /**
   * Close the connection, all associated streams will be closed before.
   *
   * @return a future signaling the completion of the operation
   */
  default Future<Void> close() {
    return close(new QuicConnectionClose());
  }

  /**
   * Close the connection, all associated streams will be closed before.
   *
   * @param payload the close payload
   * @return a future signaling the completion of the operation
   */
  default Future<Void> close(QuicConnectionClose payload) {
    return shutdown(Duration.ZERO, payload);
  }

  /**
   * Shutdown with a 30 seconds timeout.
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(Duration.ofSeconds(30));
  }

  /**
   * Initiate a graceful connection shutdown, the connection is taken out of service and closed when all the inflight streams
   * are processed, otherwise after a {@code timeout} the connection will be closed.
   *
   * @param timeout the amount of time after which all resources are forcibly closed
   * @return a future completed when shutdown has completed
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default Future<Void> shutdown(Duration timeout) {
    return shutdown(timeout, new QuicConnectionClose());
  }

  /**
   * Initiate a graceful connection shutdown, the connection is taken out of service and closed when all the inflight streams
   * are processed, otherwise after a {@code timeout} the connection will be closed.
   *
   * @param timeout the amount of time after which all resources are forcibly closed
   * @param payload the close payload
   * @return a future completed when shutdown has completed
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> shutdown(Duration timeout, QuicConnectionClose payload);

  /**
   * @return the application-level protocol negotiated during the TLS handshake
   */
  String applicationLayerProtocol();

  /**
   * @return the server name indicated during the TLS handshake
   */
  String indicatedServerName();

  /**
   * @return the transport parameters
   */
  QuicTransportParams transportParams();

  /**
   * @return SSLSession associated with the underlying socket. Returns null if connection is
   *         not SSL.
   * @see javax.net.ssl.SSLSession
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  SSLSession sslSession();

  /**
   * @return the close payload, available after the close handler is signaled
   */
  QuicConnectionClose closePayload();

  /**
   * @return the remote connection socket address
   */
  SocketAddress remoteAddress();

  /**
   * @return the local connection socket address
   */
  SocketAddress localAddress();

}
