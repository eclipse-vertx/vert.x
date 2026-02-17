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
import io.vertx.core.spi.metrics.MetricsProvider;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class HttpClientBase implements MetricsProvider, Closeable {

  protected final VertxInternal vertx;
  protected final ProxyOptions defaultProxyOptions;
  protected final HttpClientMetrics<?, ?> httpMetrics;
  protected final CloseSequence closeSequence;
  private Duration closeTimeout = Duration.ZERO;
  private Predicate<SocketAddress> proxyFilter;

  public HttpClientBase(VertxInternal vertx,
                        HttpClientMetrics<?, ?> httpMetrics,
                        ProxyOptions defaultProxyOptions,
                        List<String> nonProxyHosts) {
    this.vertx = vertx;
    this.httpMetrics = httpMetrics;
    this.defaultProxyOptions = defaultProxyOptions;
    this.closeSequence = new CloseSequence(p -> doClose(p), p1 -> doShutdown(closeTimeout, p1));
    this.proxyFilter = nonProxyHosts != null ? ProxyFilter.nonProxyHosts(nonProxyHosts) : ProxyFilter.DEFAULT_PROXY_FILTER;
  }

  static void configureSSLOptions(boolean verifyHost, boolean useAlpn, ClientSSLOptions sslOptions) {
    if (sslOptions.getHostnameVerificationAlgorithm() == null) {
      sslOptions.setHostnameVerificationAlgorithm(verifyHost ? "HTTPS" : "");
    }
    sslOptions.setUseAlpn(useAlpn);
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

  protected static ClientSSLOptions sslOptions(boolean verifyHost, boolean useAlpn, HttpConnectOptions connectOptions, ClientSSLOptions defaultSslOptions) {
    ClientSSLOptions sslOptions = connectOptions.getSslOptions();
    if (sslOptions != null) {
      sslOptions = sslOptions.copy();
      configureSSLOptions(verifyHost, useAlpn, sslOptions);
    } else {
      sslOptions = defaultSslOptions;
    }
    return sslOptions;
  }

  public HttpClientMetrics metrics() {
    return httpMetrics;
  }

  protected abstract void doShutdown(Duration timeout, Completable<Void> p);

  protected abstract void doClose(Completable<Void> p);

  public Future<Void> shutdown(Duration timeout) {
    this.closeTimeout = timeout;
    return closeSequence.close();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getMetrics() != null;
  }

  @Override
  public HttpClientMetrics<?, ?> getMetrics() {
    return httpMetrics;
  }

  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    options = options.copy();
    setDefaultSslOptions(options);
    return Future.succeededFuture(true);
  }

  protected abstract void setDefaultSslOptions(ClientSSLOptions options);

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
