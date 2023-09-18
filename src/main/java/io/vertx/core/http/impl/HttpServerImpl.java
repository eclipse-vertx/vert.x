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

import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl extends TCPServerBase implements HttpServer, Closeable, MetricsProvider {

  static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);

  private static final Handler<Throwable> DEFAULT_EXCEPTION_HANDLER = t -> log.trace("Connection failure", t);

  private static final String DISABLE_WEBSOCKETS_PROP_NAME = "vertx.disableWebsockets";
  private static final String DISABLE_H2C_PROP_NAME = "vertx.disableH2c";

  static final boolean DISABLE_WEBSOCKETS = Boolean.getBoolean(DISABLE_WEBSOCKETS_PROP_NAME);

  final HttpServerOptions options;
  private final boolean disableH2c;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;
  private Handler<HttpConnection> connectionHandler;

  private Handler<Throwable> exceptionHandler;

  public HttpServerImpl(VertxInternal vertx, HttpServerOptions options) {
    super(vertx, options);
    this.options = (HttpServerOptions) super.options;
    this.disableH2c = Boolean.getBoolean(DISABLE_H2C_PROP_NAME) || options.isSsl();
  }

  @Override
  protected void configure(SSLOptions options) {
    List<String> applicationProtocols = this.options
      .getAlpnVersions()
      .stream()
      .map(HttpVersion::alpnName)
      .collect(Collectors.toList());
    options.setApplicationLayerProtocols(applicationProtocols);
  }

  @Override
  protected TCPMetrics<?> createMetrics(SocketAddress localAddress) {
    VertxMetrics vertxMetrics = vertx.metricsSPI();
    if (vertxMetrics != null) {
      return vertxMetrics.createHttpServerMetrics(options, localAddress);
    } else {
      return null;
    }
  }

  @Override
  public synchronized HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    requestHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    wsHandler = handler;
    return this;
  }

  @Override
  public synchronized Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  @Override
  public synchronized HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    invalidRequestHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServer connectionHandler(Handler<HttpConnection> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    connectionHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServer exceptionHandler(Handler<Throwable> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized Handler<ServerWebSocket> webSocketHandler() {
    return wsHandler;
  }

  @Override
  public Future<HttpServer> listen() {
    return listen(options.getPort(), options.getHost());
  }

  @Override
  protected Worker childHandler(ContextInternal context, SocketAddress address, GlobalTrafficShapingHandler trafficShapingHandler) {
    ContextInternal connContext;
    if (context.isEventLoopContext()) {
      connContext = context;
    } else {
      connContext = vertx.createEventLoopContext(context.nettyEventLoop(), context.workerPool(), context.classLoader());
    }
    String host = address.isInetSocket() ? address.host() : "localhost";
    int port = address.port();
    String serverOrigin = (options.isSsl() ? "https" : "http") + "://" + host + ":" + port;
    HttpServerConnectionHandler hello = new HttpServerConnectionHandler(this, requestHandler, invalidRequestHandler, wsHandler, connectionHandler, exceptionHandler == null ? DEFAULT_EXCEPTION_HANDLER : exceptionHandler);
    Supplier<ContextInternal> streamContextSupplier = context::duplicate;
    return new HttpServerWorker(
      connContext,
      streamContextSupplier,
      this,
      vertx,
      options,
      serverOrigin,
      disableH2c,
      hello,
      hello.exceptionHandler,
      trafficShapingHandler);
  }

  @Override
  public synchronized Future<HttpServer> listen(SocketAddress address) {
    if (requestHandler == null && wsHandler == null) {
      throw new IllegalStateException("Set request or WebSocket handler first");
    }
    return bind(address).map(this);
  }

  @Override
  public Future<Void> close() {
    ContextInternal context = vertx.getOrCreateContext();
    PromiseInternal<Void> promise = context.promise();
    close(promise);
    return promise.future();
  }

  public boolean isClosed() {
    return !isListening();
  }

  boolean requestAccept() {
    // Might be useful later
    return true;
  }

  boolean wsAccept() {
    // Might be useful later
    return true;
  }
}
