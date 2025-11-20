package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

import java.time.Duration;

public interface HttpServerConfig {

  HttpServerConfig setMaxFormBufferedBytes(int maxFormBufferedBytes);
  HttpServerConfig setMaxFormAttributeSize(int maxSize);
  HttpServerConfig setMaxFormFields(int maxFormFields);
  HttpServerConfig setIdleTimeout(Duration timeout);
  HttpServerConfig setLogActivity(boolean logActivity);
  HttpServerConfig setHandle100ContinueAutomatically(boolean b);
  HttpServer create(Vertx vertx);

}
