package io.vertx.core.http.impl;

import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

public class HttpConnectParams {

  ClientSSLOptions sslOptions;
  ProxyOptions proxyOptions;
  boolean ssl;

}
