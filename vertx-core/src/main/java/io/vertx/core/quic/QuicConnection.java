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
package io.vertx.core.quic;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLSession;

/**
 * <p>A Quic connection between a client and a server, providing support for handling or creating Quic {@link QuicStream streams}</p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface QuicConnection {

  /**
   * Set a handler processing incoming Quic streams.
   *
   * @param handler the handler processing streams
   * @return this instance of a connection
   */
  QuicConnection handler(Handler<QuicStream> handler);

  /**
   * Set a handler called when the connection is closed.
   *
   * @param handler the handler signaled with the connection close
   * @return this instance of a connection
   */
  QuicConnection closeHandler(Handler<Void> handler);

  /**
   * Create a bidirectional stream to the remote endpoint.
   *
   * @return a Quic stream as a future
   */
  default Future<QuicStream> createStream() {
    return createStream(true);
  }

  /**
   * Create a stream to the remote endpoint.
   *
   * @param bidirectional whether the stream is unidirectional or bidirectional
   * @return A Quic stream as a future
   */
  Future<QuicStream> createStream(boolean bidirectional);

  /**
   * Close the connection, all associated streams will be closed before.
   *
   * @return a future signaling the completion of the operation
   */
  Future<Void> close();

  /**
   * Close the connection, all associated streams will be closed before.
   *
   * @param payload the close payload
   * @return a future signaling the completion of the operation
   */
  Future<Void> close(ConnectionClose payload);

  /**
   * @return the application-level protocol negotiated during the TLS handshake
   */
  String applicationLayerProtocol();

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
  ConnectionClose closePayload();

  /**
   * @return the remote connection socket address
   */
  SocketAddress remoteAddress();

  /**
   * @return the local connection socket address
   */
  SocketAddress localAddress();

}
