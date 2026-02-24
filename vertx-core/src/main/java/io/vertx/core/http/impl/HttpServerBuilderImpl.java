/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerBuilder;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.quic.QuicHttpServer;
import io.vertx.core.http.impl.tcp.TcpHttpServer;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.ServerSSLOptions;

/**
 * The server builder implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerBuilderImpl implements HttpServerBuilder {

  private final VertxImpl vertx;
  private HttpServerConfig config;
  private ServerSSLOptions sslOptions;
  private SSLEngineOptions sslEngineOptions;
  private Handler<HttpConnection> connectHandler;
  private boolean registerWebSocketWriteHandlers;

  public HttpServerBuilderImpl(VertxImpl vertx) {
    this.vertx = vertx;
    this.registerWebSocketWriteHandlers = false;
  }

  @Override
  public HttpServerBuilderImpl with(HttpServerConfig config) {
    this.config = config;
    return this;
  }

  @Override
  public HttpServerBuilderImpl with(ServerSSLOptions options) {
    this.sslOptions = options;
    return this;
  }

  @Override
  public HttpServerBuilderImpl with(SSLEngineOptions engine) {
    this.sslEngineOptions = engine;
    return this;
  }

  public HttpServerBuilderImpl registerWebSocketWriteHandlers(boolean registerWebSocketWriteHandlers) {
    this.registerWebSocketWriteHandlers = registerWebSocketWriteHandlers;
    return this;
  }

  @Override
  public HttpServerBuilderImpl withConnectHandler(Handler<HttpConnection> handler) {
    this.connectHandler = handler;
    return this;
  }

  @Override
  public HttpServer build() {
    boolean useTcp = config.getVersions().contains(HttpVersion.HTTP_1_1) || config.getVersions().contains(HttpVersion.HTTP_2) || config.getVersions().contains(HttpVersion.HTTP_1_0);
    boolean useQuic = config.getVersions().contains(HttpVersion.HTTP_3);
    if (useQuic && sslOptions == null) {
      throw new NullPointerException("SSL configuration is necessary for a QUIC server");
    }
    if (useTcp && config.isSsl() && sslOptions == null) {
      throw new NullPointerException("SSL configuration is necessary for a TCP/SSL server");
    }
    HttpServer server;
    if (useTcp) {
      if (useQuic) {
        HybridHttpServer compositeServer = new HybridHttpServer(vertx, new HttpServerConfig(config), sslOptions.copy(), sslEngineOptions);
        server = new CleanableHttpServer(vertx, compositeServer);
      } else {
        if (sslOptions != null) {
          sslOptions = sslOptions.copy();
        }
        server = new CleanableHttpServer(vertx, new TcpHttpServer(vertx, new HttpServerConfig(config), sslOptions, sslEngineOptions, null, registerWebSocketWriteHandlers));
      }
    } else if (useQuic) {
      server = new CleanableHttpServer(vertx, new QuicHttpServer(vertx, new HttpServerConfig(config), sslOptions.copy(), null));
    } else {
      throw new IllegalArgumentException("You must set at least one supported HTTP version");
    }
    Handler<HttpConnection> handler = connectHandler;
    if (handler != null) {
      return server.connectionHandler(handler);
    }
    return server;
  }
}
