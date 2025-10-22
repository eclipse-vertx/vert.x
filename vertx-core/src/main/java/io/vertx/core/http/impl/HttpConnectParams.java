package io.vertx.core.http.impl;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

class HttpConnectParams {

  HttpClientOptions options;
  ClientSSLOptions sslOptions;
  ProxyOptions proxyOptions;

  HttpVersion version;
  boolean ssl;
  boolean useAlpn;

}
