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

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.impl.quic.QuicClientImpl;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.function.BiFunction;

/**
 * A Quic client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface QuicClient extends QuicEndpoint {

  /**
   * <p>Create a configured Quic client.</p>
   *
   * <p>The returned client must be bound, prior {@link #connect(SocketAddress) connecting} to a server.</p>
   *
   * @param vertx the vertx instance
   * @param config the client configuration
   * @return the client
   */
  static QuicClient create(Vertx vertx, QuicClientConfig config, ClientSSLOptions sslOptions) {
    VertxInternal vertxInternal = (VertxInternal) vertx;
    VertxMetrics metrics = vertxInternal.metrics();
    TransportMetrics<?> clientMetrics;
    if (metrics != null) {
      clientMetrics = metrics.createQuicEndpointMetrics(config);
    } else {
      clientMetrics = null;
    }
    return QuicClientImpl.create(vertxInternal, clientMetrics, config, sslOptions);
  }

  /**
   * Connect to a Quic server.
   *
   * @param address the server address
   * @return a Quic connection as a future
   */
  default Future<QuicConnection> connect(SocketAddress address) {
    return connect(address, QuicClientImpl.DEFAULT_CONNECT_OPTIONS);
  }

  /**
   * Connect to a Quic server with a specific {@code sslOptions}.
   *
   * @param address the server address
   * @param options the connect options
   * @return a Quic connection as a future
   */
  Future<QuicConnection> connect(SocketAddress address, QuicConnectOptions options);

}
