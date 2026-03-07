package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientBuilder;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;

import java.time.Duration;
import java.util.function.Consumer;

public interface HttpClientConfig {
  HttpClientConfig setSsl(boolean ssl);
  HttpClientConfig setVerifyHost(boolean verify);
  HttpClientConfig setForceSni(boolean forceSni);
  HttpClientConfig setProxyOptions(ProxyOptions proxyOptions);
  HttpClientConfig setConnectTimeout(Duration connectTimeout);
  HttpClientConfig setIdleTimeout(Duration timeout);
  HttpClientConfig setKeepAliveTimeout(Duration timeout);
  HttpClientConfig setDecompressionSupported(boolean decompressionSupported);
  HttpClientConfig setLocalAddress(String localAddress);
  HttpClientConfig setLogActivity(boolean logActivity);
  HttpClientConfig setMetricsName(String name);
  HttpClientConfig configureSsl(Consumer<ClientSSLOptions> configurator);
  default HttpClientAgent create(Vertx vertx, PoolOptions poolOptions) {
    return builder(vertx).with(poolOptions).build();
  }
  default HttpClientAgent create(Vertx vertx) {
    return builder(vertx).build();
  }
  HttpClientBuilder builder(Vertx vertx);
}
