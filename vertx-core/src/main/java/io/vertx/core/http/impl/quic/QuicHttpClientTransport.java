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
package io.vertx.core.http.impl.quic;

import io.netty.handler.codec.http3.Http3;
import io.vertx.core.Future;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpConnectParams;
import io.vertx.core.http.impl.http3.Http3ClientConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.quic.QuicClientImpl;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.time.Duration;
import java.util.Arrays;

public class QuicHttpClientTransport implements HttpClientTransport {

  private final VertxInternal vertx;
  private final QuicClient client;
  private final long keepAliveTimeoutMillis;
  private final Http3Settings localSettings;

  public QuicHttpClientTransport(VertxInternal vertx, HttpClientConfig config) {

    QuicClientConfig quicConfig = new QuicClientConfig(config.getQuicConfig());
    quicConfig.setMetricsName(config.getMetricsName());

    Http3Settings localSettings = config.getHttp3Config().getInitialSettings();
    if (localSettings == null) {
      localSettings = new Http3Settings();
    }

    QuicClient client = new QuicClientImpl(vertx, quicConfig, "http", null);

    this.vertx = vertx;
    this.keepAliveTimeoutMillis = config.getHttp3Config().getKeepAliveTimeout() == null ? 0L : config.getHttp3Config().getKeepAliveTimeout().toMillis();
    this.localSettings = localSettings;
    this.client = client;
  }

  @Override
  public Future<HttpClientConnection> connect(ContextInternal context, SocketAddress server, HostAndPort authority, HttpConnectParams params, ClientMetrics<?, ?, ?> clientMetrics) {
    ClientSSLOptions sslOptions = params.sslOptions;
    if (sslOptions == null) {
      return context.failedFuture("Missing clients SSL options");
    }
    sslOptions = sslOptions
      .copy()
      .setUseAlpn(true)
      .setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    QuicConnectOptions connectOptions = new QuicConnectOptions();
    connectOptions.setServerName(authority.host());
    connectOptions.setSslOptions(sslOptions);
    Future<QuicConnection> f = client.connect(server, connectOptions);
    return f.map(res -> {
      Http3ClientConnection c = new Http3ClientConnection(
        (QuicConnectionInternal) res,
        authority,
        (ClientMetrics<Object, HttpRequest, HttpResponse>) clientMetrics,
        keepAliveTimeoutMillis,
        localSettings);
      c.init();
      return c;
    });
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return client.shutdown(timeout);
  }

}
