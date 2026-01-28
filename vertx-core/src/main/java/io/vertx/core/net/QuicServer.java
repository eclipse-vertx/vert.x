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
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.impl.quic.QuicServerImpl;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.function.BiFunction;

/**
 * A Quic server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface QuicServer extends QuicEndpoint {

  /**
   * <p>Create a configured Quic server.</p>
   *
   * <p>The returned server can be bound, after setting a connection {@link #handler(Handler) handler}</p>
   *
   * @param vertx the vertx instance
   * @param options the server configuration options
   * @return the server
   */
  static QuicServer create(Vertx vertx, QuicServerConfig options, ServerSSLOptions sslOptions) {
    VertxInternal vertxInternal = (VertxInternal) vertx;
    VertxMetrics metrics = vertxInternal.metrics();
    BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider;
    if (metrics != null) {
      metricsProvider = metrics::createQuicEndpointMetrics;
    } else {
      metricsProvider = null;
    }
    return QuicServerImpl.create((VertxInternal) vertx, metricsProvider, options, sslOptions);
  }

  /**
   * Set the handler processing {@link QuicConnection}, the handler must be set before the server is bound.
   * @param handler the connection handler
   * @return this object instance
   */
  QuicServer handler(Handler<QuicConnection> handler);

}
