package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.CompressionConfig;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerBuilder;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.net.ServerSSLOptions;

import java.time.Duration;
import java.util.function.Consumer;

public interface HttpServerConfigurator {

  HttpServerConfigurator setSsl(boolean ssl);
  HttpServerConfigurator setUseProxyProtocol(boolean useProxyProtocol);
  HttpServerConfigurator setDecompressionSupported(boolean supported);
  HttpServerConfigurator setCompression(CompressionConfig compression);
  HttpServerConfigurator setMaxFormBufferedBytes(int maxFormBufferedBytes);
  HttpServerConfigurator setMaxFormAttributeSize(int maxSize);
  HttpServerConfigurator setMaxFormFields(int maxFormFields);
  HttpServerConfigurator setIdleTimeout(Duration timeout);
  HttpServerConfigurator setLogActivity(boolean logActivity);
  HttpServerConfigurator setHandle100ContinueAutomatically(boolean b);
  HttpServerConfigurator configureSsl(Consumer<ServerSSLOptions> configurator);
  HttpServerConfig config();
  ServerSSLOptions sslOptions();
  default HttpServer create(Vertx vertx) {
    return builder(vertx).build();
  }
  HttpServerBuilder builder(Vertx vertx);

}
