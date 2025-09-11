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

import javax.net.ssl.SSLSession;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface QuicConnection {

  QuicConnection handler(Handler<QuicStream> handler);

  QuicConnection closeHandler(Handler<Void> handler);

  Future<QuicStream> createStream();

  Future<Void> close();

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

  ConnectionClose closePayload();

}
