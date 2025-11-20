package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientBuilder;
import io.vertx.core.http.PoolOptions;

import java.time.Duration;

public interface HttpClientConfig {
  HttpClientConfig setConnectTimeout(Duration connectTimeout);
  HttpClientConfig setIdleTimeout(Duration timeout);
  HttpClientConfig setKeepAliveTimeout(Duration timeout);
  HttpClientConfig setDecompressionSupported(boolean decompressionSupported);
  HttpClientConfig setLocalAddress(String localAddress);
  HttpClientConfig setLogActivity(boolean logActivity);
  default HttpClientAgent create(Vertx vertx, PoolOptions poolOptions) {
    return builder(vertx).with(poolOptions).build();
  }
  default HttpClientAgent create(Vertx vertx) {
    return builder(vertx).build();
  }
  HttpClientBuilder builder(Vertx vertx);
}
