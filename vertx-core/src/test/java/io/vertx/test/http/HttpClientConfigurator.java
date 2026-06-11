package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientBuilder;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;

import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;

public interface HttpClientConfigurator {
  HttpClientConfigurator setSsl(boolean ssl);
  HttpClientConfigurator setVerifyHost(boolean verify);
  HttpClientConfigurator setForceSni(boolean forceSni);
  HttpClientConfigurator setProxyOptions(ProxyOptions proxyOptions);
  HttpClientConfigurator setConnectTimeout(Duration connectTimeout);
  HttpClientConfigurator setIdleTimeout(Duration timeout);
  HttpClientConfigurator setKeepAliveTimeout(Duration timeout);
  HttpClientConfigurator setDecompressionSupported(boolean decompressionSupported);
  HttpClientConfigurator setLocalAddress(String localAddress);
  HttpClientConfigurator setLogActivity(boolean logActivity);
  HttpClientConfigurator setMetricsName(String name);
  HttpClientConfigurator configureSsl(Consumer<ClientSSLOptions> configurator);
  HttpClientConfigurator setMaxRedirectBufferSize(int maxRedirectBufferSize);
  HttpClientConfigurator setSameOriginRedirectBlockedHeaders(Set<String> headers);
  HttpClientConfigurator setCrossOriginRedirectBlockedHeaders(Set<String> headers);
  default HttpClientAgent create(Vertx vertx, PoolOptions poolOptions) {
    return builder(vertx).with(poolOptions).build();
  }
  default HttpClientAgent create(Vertx vertx) {
    return builder(vertx).build();
  }
  HttpClientBuilder builder(Vertx vertx);
}
