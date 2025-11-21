package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.PoolOptions;

import java.time.Duration;

public interface HttpClientConfig {
  HttpClientConfig setConnectTimeout(Duration connectTimeout);
  HttpClientConfig setIdleTimeout(Duration timeout);
  HttpClientConfig setKeepAliveTimeout(Duration timeout);
  HttpClientConfig setDecompressionSupported(boolean decompressionSupported);
  HttpClientConfig setLocalAddress(String localAddress);
  HttpClientConfig setLogActivity(boolean logActivity);
  HttpClientAgent create(Vertx vertx, PoolOptions poolOptions);
  HttpClientAgent create(Vertx vertx);
}
