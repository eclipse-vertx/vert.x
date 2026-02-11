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
import io.vertx.core.http.*;
import io.vertx.core.http.impl.quic.QuicHttpServer;
import io.vertx.core.http.impl.tcp.TcpHttpServer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrafficShapingOptions;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;

import java.time.Duration;
import java.util.List;

/**
 * A hybrid HTTP server combining TCP and QUIC protocols.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HybridHttpServer implements HttpServerInternal {

  private final VertxInternal vertx;
  private final HttpServerConfig config;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;
  private Handler<HttpConnection> connectionHandler;
  private Handler<ServerWebSocket> webSocketHandler;
  private Handler<ServerWebSocketHandshake> webSocketHandshakeHandler;
  private Handler<Throwable> exceptionHandler;
  private HttpServerInternal tcpServer;
  private HttpServerInternal quicServer;
  private HttpServerMetrics<?, ?> httpMetrics;

  public HybridHttpServer(VertxInternal vertx, HttpServerConfig config) {
    this.vertx = vertx;
    this.config = config;
  }

  public HttpServerInternal tcpServer(HttpServerMetrics<?, ?> httpMetrics,  HttpServerConfig config) {
    if (tcpServer == null) {
      TcpHttpServer server = new TcpHttpServer(vertx, config, httpMetrics, false);
      setHandlers(server);
      tcpServer = server;
    }
    return tcpServer;
  }

  public HttpServerInternal quicServer(HttpServerMetrics<?, ?> httpMetrics,  HttpServerConfig config) {
    if (quicServer == null) {
      QuicHttpServer server = new QuicHttpServer(vertx, config, httpMetrics);
      setHandlers(server);
      quicServer = server;
    }
    return quicServer;
  }

  private void setHandlers(HttpServer server) {
    server.requestHandler(requestHandler);
    server.invalidRequestHandler(invalidRequestHandler);
    server.connectionHandler(connectionHandler);
    server.webSocketHandler(webSocketHandler);
    server.exceptionHandler(exceptionHandler);
    server.webSocketHandshakeHandler(webSocketHandshakeHandler);
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    this.requestHandler = handler;
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    this.invalidRequestHandler = handler;
    return this;
  }

  @Override
  public HttpServer connectionHandler(Handler<HttpConnection> handler) {
    this.connectionHandler = handler;
    return this;
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    this.webSocketHandshakeHandler = handler;
    return this;
  }

  @Override
  public HttpServer exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    this.webSocketHandler = handler;
    return this;
  }

  @Override
  public Handler<ServerWebSocket> webSocketHandler() {
    return webSocketHandler;
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
    SocketAddress tcpLocalAddress = SocketAddress.inetSocketAddress(config.getTcpPort(), config.getTcpHost());
    SocketAddress udpLocalAddress = SocketAddress.inetSocketAddress(config.getQuicPort(), config.getQuicHost());
    httpMetrics = vertx.metrics() != null ? vertx.metrics().createHttpServerMetrics(config, tcpLocalAddress, udpLocalAddress) : null;
    return listen(tcpServer(httpMetrics, config).listen(context),
      quicServer(httpMetrics, config).listen(context));
  }

  @Override
  public Future<HttpServer> listen(ContextInternal context, SocketAddress address) {
    if (!address.isInetSocket()) {
      return context.failedFuture("HTTP/3 enabled server can only be bound to an inet socket address");
    }
    HttpServerConfig config = new HttpServerConfig(this.config);
    config.setHost(address.host());
    config.setPort(address.port());
    httpMetrics = vertx.metrics() != null ? vertx.metrics().createHttpServerMetrics(config, address, address) : null;
    return listen(tcpServer(httpMetrics, config).listen(context, address), quicServer(httpMetrics, config).listen(context, address));
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
    HttpServerInternal tcpServer = this.tcpServer;
    HttpServerInternal quicServer = this.quicServer;
    HttpServerMetrics<?, ?> httpMetrics = this.httpMetrics;
    this.tcpServer = null;
    this.quicServer = null;
    this.httpMetrics = null;
    Future<?> root = Future
      .join(tcpServer.shutdown(timeout), quicServer.shutdown(timeout));
    if (httpMetrics != null) {
      root = root.andThen(ar -> httpMetrics.close());
    }
    return root
      .mapEmpty();
  }

  @Override
  public int actualPort() {
    HttpServerInternal tcpServer = this.tcpServer;
    return tcpServer != null ? tcpServer.actualPort() : -1;
  }

  @Override
  public boolean isClosed() {
    HttpServerInternal tcpServer = this.tcpServer;
    HttpServerInternal quicServer = this.quicServer;
    return (tcpServer == null || tcpServer.isClosed()) && (quicServer == null || quicServer.isClosed());
  }

  @Override
  public Metrics getMetrics() {
    return httpMetrics;
  }
}
