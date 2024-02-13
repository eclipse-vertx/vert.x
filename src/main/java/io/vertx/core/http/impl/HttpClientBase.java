/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseSequence;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.net.impl.NetClientInternal;
import io.vertx.core.net.impl.ProxyFilter;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class is thread-safe.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientBase implements MetricsProvider, Closeable {

  protected final VertxInternal vertx;
  final HttpClientOptions options;
  protected final NetClientInternal netClient;
  protected final List<String> alpnVersions;
  protected final HttpClientMetrics metrics;
  protected final CloseSequence closeSequence;
  private volatile ClientSSLOptions defaultSslOptions;
  private long closeTimeout = 0L;
  private TimeUnit closeTimeoutUnit = TimeUnit.SECONDS;
  private Predicate<SocketAddress> proxyFilter;

  public HttpClientBase(VertxInternal vertx, HttpClientOptions options) {
    if (!options.isKeepAlive() && options.isPipelining()) {
      throw new IllegalStateException("Cannot have pipelining with no keep alive");
    }
    List<HttpVersion> alpnVersions = options.getAlpnVersions();
    if (alpnVersions == null || alpnVersions.isEmpty()) {
      switch (options.getProtocolVersion()) {
        case HTTP_2:
          alpnVersions = Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);
          break;
        default:
          alpnVersions = Collections.singletonList(options.getProtocolVersion());
          break;
      }
    } else {
      alpnVersions = new ArrayList<>(alpnVersions);
    }
    this.alpnVersions = alpnVersions.stream().map(HttpVersion::alpnName).collect(Collectors.toUnmodifiableList());
    this.vertx = vertx;
    this.metrics = vertx.metricsSPI() != null ? vertx.metricsSPI().createHttpClientMetrics(options) : null;
    this.options = new HttpClientOptions(options);
    this.closeSequence = new CloseSequence(this::doClose, this::doShutdown);
    this.proxyFilter = options.getNonProxyHosts() != null ? ProxyFilter.nonProxyHosts(options.getNonProxyHosts()) : ProxyFilter.DEFAULT_PROXY_FILTER;
    this.netClient = new NetClientBuilder(vertx, new NetClientOptions(options).setProxyOptions(null)).metrics(metrics).build();
    this.defaultSslOptions = options.getSslOptions();

    ClientSSLOptions sslOptions = options.getSslOptions();
    if (sslOptions != null) {
      configureSSLOptions(sslOptions);
    }
  }

  private void configureSSLOptions(ClientSSLOptions sslOptions) {
    if (sslOptions.getHostnameVerificationAlgorithm() == null) {
      sslOptions.setHostnameVerificationAlgorithm(options.isVerifyHost() ? "HTTPS" : "");
    }
    if (sslOptions.getApplicationLayerProtocols() == null) {
      sslOptions.setApplicationLayerProtocols(alpnVersions);
    }
  }

  public NetClientInternal netClient() {
    return netClient;
  }

  public Future<Void> closeFuture() {
    return closeSequence.future();
  }

  public void close(Promise<Void> completion) {
    closeSequence.close(completion);
  }

  protected int getPort(RequestOptions request) {
    Integer port = request.getPort();
    if (port != null) {
      return port;
    }
    SocketAddress server = (SocketAddress) request.getServer();
    if (server != null && server.isInetSocket()) {
      return server.port();
    }
    return options.getDefaultPort();
  }

  private ProxyOptions getProxyOptions(ProxyOptions proxyOptions) {
    if (proxyOptions == null) {
      proxyOptions = options.getProxyOptions();
    }
    return proxyOptions;
  }

  protected String getHost(RequestOptions request) {
    String host = request.getHost();
    if (host != null) {
      return host;
    }
    SocketAddress server = (SocketAddress) request.getServer();
    if (server != null && server.isInetSocket()) {
      return server.host();
    }
    return options.getDefaultHost();
  }

  protected ProxyOptions computeProxyOptions(ProxyOptions proxyOptions, SocketAddress addr) {
    proxyOptions = getProxyOptions(proxyOptions);
    if (proxyFilter != null) {
      if (!proxyFilter.test(addr)) {
        proxyOptions = null;
      }
    }
    return proxyOptions;
  }

  protected ClientSSLOptions sslOptions(HttpConnectOptions connectOptions) {
    ClientSSLOptions sslOptions = connectOptions.getSslOptions();
    if (sslOptions != null) {
      sslOptions = sslOptions.copy();
      configureSSLOptions(sslOptions);
    } else {
      sslOptions = defaultSslOptions;
    }
    return sslOptions;
  }

  HttpClientMetrics metrics() {
    return metrics;
  }

  protected void doShutdown(Promise<Void> p) {
    netClient.shutdown(closeTimeout, closeTimeoutUnit).onComplete(p);
  }

  protected void doClose(Promise<Void> p) {
    netClient.close().onComplete(p);
  }

  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    this.closeTimeout = timeout;
    this.closeTimeoutUnit = unit;
    return closeSequence.close();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getMetrics() != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    options = options.copy();
    configureSSLOptions(options);
    defaultSslOptions = options;
    return Future.succeededFuture(true);
  }

  public HttpClientBase proxyFilter(Predicate<SocketAddress> filter) {
    proxyFilter = filter;
    return this;
  }

  public HttpClientOptions options() {
    return options;
  }

  public VertxInternal vertx() {
    return vertx;
  }

  protected void checkClosed() {
    if (closeSequence.started()) {
      throw new IllegalStateException("Client is closed");
    }
  }
}
