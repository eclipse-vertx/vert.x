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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.ServiceResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrafficShapingOptions;
import io.vertx.core.spi.metrics.Metrics;

import java.time.Duration;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableHttpServer extends ServiceResource<SocketAddress, HttpServer> implements HttpServerInternal {

  private final VertxInternal vertx;
  private final HttpServerInternal server;

  public CleanableHttpServer(VertxInternal vertx, HttpServerInternal server) {
    this.vertx = vertx;
    this.server = Objects.requireNonNull(server);
  }

  @Override
  protected Future<HttpServer> startImpl(ContextInternal context, SocketAddress address) {
    Future<HttpServer> fut;
    if (address == null) {
      fut = server.listen(context);
    } else {
      fut = server.listen(context, address);
    }
    return fut.map(this);
  }

  @Override
  protected Future<Void> stopImpl(ContextInternal context, SocketAddress args, Duration timeout) {
    return server.shutdown(timeout);
  }

  @Override
  public Future<HttpServer> listen() {
    return listen(vertx.getOrCreateContext());
  }

  @Override
  public Future<HttpServer> listen(SocketAddress address) {
    return listen(vertx.getOrCreateContext(), address);
  }

  @Override
  public Future<HttpServer> listen(ContextInternal context) {
    return listen(context, null);
  }

  public Future<HttpServer> listen(ContextInternal context, SocketAddress address) {
    return start(context, address);
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return stop(vertx.getOrCreateContext(), timeout);
  }

  @Override
  public boolean isClosed() {
    return server.isClosed();
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    server.requestHandler(handler);
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return server.requestHandler();
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    server.invalidRequestHandler(handler);
    return this;
  }

  @Override
  public HttpServer connectionHandler(Handler<HttpConnection> handler) {
    server.connectionHandler(handler);
    return this;
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    server.webSocketHandshakeHandler(handler);
    return this;
  }

  @Override
  public HttpServer exceptionHandler(Handler<Throwable> handler) {
    server.exceptionHandler(handler);
    return this;
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    server.webSocketHandler(handler);
    return this;
  }

  @Override
  public Handler<ServerWebSocket> webSocketHandler() {
    return server.webSocketHandler();
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    return server.updateSSLOptions(options, force);
  }

  @Override
  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    return server.updateTrafficShapingOptions(options);
  }

  @Override
  public int actualPort() {
    return server.actualPort();
  }

  @Override
  public Metrics getMetrics() {
    return server.getMetrics();
  }

  @Override
  public HttpServerInternal unwrap() {
    return server;
  }
}
