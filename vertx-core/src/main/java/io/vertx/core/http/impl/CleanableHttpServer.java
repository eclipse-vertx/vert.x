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

import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrafficShapingOptions;
import io.vertx.core.spi.metrics.Metrics;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableHttpServer implements HttpServerInternal, Closeable {

  private final VertxInternal vertx;
  private final HttpServerInternal server;
  private ContextInternal listenContext;

  public CleanableHttpServer(VertxInternal vertx, HttpServerInternal server) {
    this.vertx = vertx;
    this.server = Objects.requireNonNull(server);
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
    synchronized (this) {
      if (listenContext != null) {
        return context.failedFuture(new IllegalStateException());
      }
      listenContext = context;
    }
    context.addCloseHook(this);
    Future<HttpServer> fut;
    if (address == null) {
      fut = server.listen(context);
    } else {
      fut = server.listen(context, address);
    }
    return fut
      .andThen(ar -> {
      if (ar.failed()) {
        synchronized (CleanableHttpServer.this) {
          if (listenContext == null) {
            return;
          }
          listenContext = null;
        }
        context.removeCloseHook(this);
      }
    });
  }

  @Override
  public void close(Completable<Void> completion) {
    close().onComplete(completion);
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    ContextInternal context;
    synchronized (this) {
      if (listenContext == null) {
        return vertx.succeededFuture();
      }
      context = listenContext;
      listenContext = null;
    }
    context.removeCloseHook(this);
    return server.shutdown(timeout, unit);
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
}
