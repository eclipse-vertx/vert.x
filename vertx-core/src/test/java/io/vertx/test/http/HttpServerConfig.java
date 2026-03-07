package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.CompressionConfig;
import io.vertx.core.http.HttpClientBuilder;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerBuilder;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ServerSSLOptions;

import java.time.Duration;
import java.util.function.Consumer;

public interface HttpServerConfig {

  HttpServerConfig setSsl(boolean ssl);
  HttpServerConfig setUseProxyProtocol(boolean useProxyProtocol);
  HttpServerConfig setDecompressionSupported(boolean supported);
  HttpServerConfig setCompression(CompressionConfig compression);
  HttpServerConfig setMaxFormBufferedBytes(int maxFormBufferedBytes);
  HttpServerConfig setMaxFormAttributeSize(int maxSize);
  HttpServerConfig setMaxFormFields(int maxFormFields);
  HttpServerConfig setIdleTimeout(Duration timeout);
  HttpServerConfig setLogActivity(boolean logActivity);
  HttpServerConfig setHandle100ContinueAutomatically(boolean b);
  HttpServerConfig configureSsl(Consumer<ServerSSLOptions> configurator);
  default HttpServer create(Vertx vertx) {
    return builder(vertx).build();
  }
  HttpServerBuilder builder(Vertx vertx);

}
