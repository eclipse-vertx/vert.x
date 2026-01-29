package io.vertx.core.http.impl;

import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

public class HttpConnectParams {

  public HttpConnectParams(HttpVersion protocol, ClientSSLOptions sslOptions, ProxyOptions proxyOptions, boolean ssl) {
    this.protocol = protocol;
    this.sslOptions = sslOptions;
    this.proxyOptions = proxyOptions;
    this.ssl = ssl;
  }

  public final HttpVersion protocol;
  public final ClientSSLOptions sslOptions;
  public final ProxyOptions proxyOptions;
  public final boolean ssl;

}
