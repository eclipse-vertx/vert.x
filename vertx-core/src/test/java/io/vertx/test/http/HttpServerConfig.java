package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

public interface HttpServerConfig {

  HttpServerConfig setLogActivity(boolean logActivity);
  HttpServerConfig setHandle100ContinueAutomatically(boolean b);
  HttpServer create(Vertx vertx);

}
