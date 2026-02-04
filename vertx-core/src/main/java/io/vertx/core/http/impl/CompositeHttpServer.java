/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrafficShapingOptions;
import io.vertx.core.spi.metrics.Metrics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A composite server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeHttpServer implements HttpServerInternal {

  private final VertxInternal vertx;
  private final HttpServerInternal tcpServer;
  private final HttpServerInternal quicServer;

  public CompositeHttpServer(VertxInternal vertx, HttpServerInternal tcpServer, HttpServerInternal quicServer) {
    this.vertx = vertx;
    this.tcpServer = tcpServer;
    this.quicServer = quicServer;
  }

  public HttpServer tcpServer() {
    return tcpServer;
  }

  public HttpServer quicServer() {
    return quicServer;
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    tcpServer.requestHandler(handler);
    quicServer.requestHandler(handler);
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return tcpServer.requestHandler();
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    tcpServer.invalidRequestHandler(handler);
    quicServer.invalidRequestHandler(handler);
    return this;
  }

  @Override
  public HttpServer connectionHandler(Handler<HttpConnection> handler) {
    tcpServer.connectionHandler(handler);
    quicServer.connectionHandler(handler);
    return this;
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    tcpServer.webSocketHandshakeHandler(handler);
    return this;
  }

  @Override
  public HttpServer exceptionHandler(Handler<Throwable> handler) {
    tcpServer.exceptionHandler(handler);
    quicServer.exceptionHandler(handler);
    return this;
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    tcpServer.webSocketHandler(handler);
    return this;
  }

  @Override
  public Handler<ServerWebSocket> webSocketHandler() {
    return tcpServer.webSocketHandler();
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    return tcpServer.updateSSLOptions(options, force);
  }

  @Override
  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    return tcpServer.updateTrafficShapingOptions(options);
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
    return listen(tcpServer.listen(context), quicServer.listen(context));
  }

  @Override
  public Future<HttpServer> listen(ContextInternal context, SocketAddress address) {
    return listen(tcpServer.listen(context, address), quicServer.listen(context, address));
  }

  private Future<HttpServer> listen(Future<HttpServer> f1, Future<HttpServer> f2) {
    List<Future<HttpServer>> list = List.of(f1, f2);
    CompositeFuture composite = Future.join(list);
    return composite.transform(ar -> {
      Future<HttpServer> root = (Future) ar;
      if (ar.failed()) {
        for (int i = 0;i < 2;i++) {
          int var = i;
          if (composite.succeeded(var)) {
            root = root.eventually(() -> list.get(var).result().close());
          }
        }
        return root;
      } else {
        return root.map(this);
      }
    });
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return Future
      .join(tcpServer.shutdown(timeout), quicServer.shutdown(timeout))
      .mapEmpty();
  }

  @Override
  public int actualPort() {
    return tcpServer.actualPort();
  }

  @Override
  public boolean isClosed() {
    return tcpServer.isClosed() && quicServer.isClosed();
  }

  @Override
  public Metrics getMetrics() {
    return tcpServer.getMetrics();
  }
}
