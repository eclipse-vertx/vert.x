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
package io.vertx.core.http.impl;

import io.netty.handler.codec.http3.Http3;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.impl.http3.Http3ClientConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.quic.QuicClientImpl;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

public class Http3ClientTransport implements HttpClientTransport {

  private final VertxInternal vertx;
  private final HttpClientMetrics<?, ?, ?> clientMetrics;
  private final Lock lock;
  private Future<QuicClient> clientFuture;
  private final QuicClientConfig quicConfig;
  private final ClientSSLOptions sslOptions;
  private final long keepAliveTimeoutMillis;
  private final Http3Settings localSettings;

  public Http3ClientTransport(VertxInternal vertxInternal, HttpClientMetrics<?, ?, ?> clientMetrics, HttpClientConfig options) {

    ClientSSLOptions sslOptions = options.getSslOptions()
      .copy()
      .setUseAlpn(true)
      .setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));

    QuicClientConfig quicConfig = new QuicClientConfig(options.getQuicConfig());

    Http3Settings localSettings = options.getHttp3Config().getInitialSettings();
    if (localSettings == null) {
      localSettings = new Http3Settings();
    }

    this.vertx = vertxInternal;
    this.clientMetrics = clientMetrics;
    this.lock = new ReentrantLock();
    this.quicConfig = quicConfig;
    this.keepAliveTimeoutMillis = options.getHttp3Config().getKeepAliveTimeout() == null ? 0L : options.getHttp3Config().getKeepAliveTimeout().toMillis();
    this.localSettings = localSettings;
    this.sslOptions = sslOptions;
  }

  @Override
  public Future<HttpClientConnection> connect(ContextInternal context, SocketAddress server, HostAndPort authority, HttpConnectParams params, ClientMetrics<?, ?, ?> metrics) {

    lock.lock();
    Future<QuicClient> fut = clientFuture;
    if (fut == null) {
      BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider;
      if (clientMetrics != null) {
        metricsProvider = (quicEndpointOptions, socketAddress) -> clientMetrics;
      } else {
        metricsProvider = null;
      }
      QuicClient client = new QuicClientImpl(vertx, metricsProvider, quicConfig, sslOptions);
      fut = client.bind(SocketAddress.inetSocketAddress(0, "localhost")).map(client);
      clientFuture = fut;
      lock.unlock();
    } else {
      lock.unlock();
    }
    Promise<HttpClientConnection> promise = context.promise();

    fut.onComplete((res, err) -> {
      if (err == null) {
        QuicConnectOptions connectOptions = new QuicConnectOptions();
        connectOptions.setServerName(authority.host());
        Future<QuicConnection> f = res.connect(server, connectOptions);
        f.onComplete((res2, err2) -> {
          if (err2 == null) {
            Http3ClientConnection c = new Http3ClientConnection(
              (QuicConnectionInternal) res2,
              authority,
              (ClientMetrics<Object, HttpRequest, HttpResponse>) metrics,
              keepAliveTimeoutMillis,
              localSettings);
            c.init();
            promise.complete(c);
          } else {
            promise.fail(err2);
          }
        });
      } else {
        promise.fail(err);
      }
    });

    return promise.future();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    if (clientFuture == null) {
      return vertx.getOrCreateContext().succeededFuture();
    } else {
      return clientFuture.compose(client -> client.shutdown(timeout));
    }
  }

  @Override
  public Future<Void> close() {
    if (clientFuture == null) {
      return vertx.getOrCreateContext().succeededFuture();
    } else {
      return clientFuture.compose(QuicEndpoint::close);
    }
  }
}
