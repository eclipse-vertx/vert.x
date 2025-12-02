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
import io.vertx.core.internal.CloseSequence;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ProxyFilter;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class HttpClientBase implements MetricsProvider, Closeable {

  protected final VertxInternal vertx;
  protected final ProxyOptions defaultProxyOptions;
  protected final HttpClientMetrics<?, ?, ?> metrics;
  protected final CloseSequence closeSequence;
  private volatile ClientSSLOptions defaultSslOptions;
  private long closeTimeout = 0L;
  private TimeUnit closeTimeoutUnit = TimeUnit.SECONDS;
  private Predicate<SocketAddress> proxyFilter;
  private final boolean verifyHost;

  public HttpClientBase(VertxInternal vertx,
                        HttpClientMetrics<?, ?, ?> metrics,
                        ProxyOptions defaultProxyOptions,
                        ClientSSLOptions defaultSslOptions,
                        List<String> nonProxyHosts,
                        boolean verifyHost) {

    if (defaultSslOptions != null) {
      configureSSLOptions(verifyHost, defaultSslOptions);
    }

    this.vertx = vertx;
    this.metrics = metrics;
    this.defaultProxyOptions = defaultProxyOptions;
    this.closeSequence = new CloseSequence(p -> doClose(p), p1 -> doShutdown(Duration.ofMillis(closeTimeoutUnit.toMillis(closeTimeout)), p1));
    this.proxyFilter = nonProxyHosts != null ? ProxyFilter.nonProxyHosts(nonProxyHosts) : ProxyFilter.DEFAULT_PROXY_FILTER;
    this.defaultSslOptions = defaultSslOptions;
    this.verifyHost = verifyHost;
  }

  void configureSSLOptions(boolean verifyHost, ClientSSLOptions sslOptions) {
    if (sslOptions.getHostnameVerificationAlgorithm() == null) {
      sslOptions.setHostnameVerificationAlgorithm(verifyHost ? "HTTPS" : "");
    }
  }

  public Future<Void> closeFuture() {
    return closeSequence.future();
  }

  public void close(Completable<Void> completion) {
    closeSequence.close(completion);
  }

  private ProxyOptions getProxyOptions(ProxyOptions proxyOptions) {
    if (proxyOptions == null) {
      proxyOptions = defaultProxyOptions;
    }
    return proxyOptions;
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
      configureSSLOptions(verifyHost, sslOptions);
    } else {
      sslOptions = defaultSslOptions;
    }
    return sslOptions;
  }

  public HttpClientMetrics metrics() {
    return metrics;
  }

  protected abstract void doShutdown(Duration timeout, Completable<Void> p);

  protected abstract void doClose(Completable<Void> p);

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
    configureSSLOptions(verifyHost, options);
    defaultSslOptions = options;
    return Future.succeededFuture(true);
  }

  public HttpClientBase proxyFilter(Predicate<SocketAddress> filter) {
    proxyFilter = filter;
    return this;
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
