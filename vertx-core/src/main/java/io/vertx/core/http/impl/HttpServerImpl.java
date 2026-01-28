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

import io.netty.handler.codec.compression.CompressionOptions;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseSequence;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.SysProps;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.tcp.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl implements HttpServer, MetricsProvider {

  static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);

  private static final Handler<Throwable> DEFAULT_EXCEPTION_HANDLER = t -> log.trace("Connection failure", t);

  static final boolean DISABLE_WEBSOCKETS = SysProps.DISABLE_WEBSOCKETS.getBoolean();

  private final VertxInternal vertx;
  final HttpOverTcpServerConfig options;
  private final boolean registerWebSocketWriteHandlers;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> webSocketHandler;
  private Handler<ServerWebSocketHandshake> webSocketHandhakeHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;
  private Handler<HttpConnection> connectionHandler;
  private Handler<Throwable> exceptionHandler;
  private NetServerInternal tcpServer;
  private long closeTimeout = 0L;
  private TimeUnit closeTimeoutUnit = TimeUnit.SECONDS;
  private CloseSequence closeSequence;

  public HttpServerImpl(VertxInternal vertx, HttpOverTcpServerConfig options, boolean registerWebSocketWriteHandlers) {
    this.vertx = vertx;
    this.options = options;
    this.registerWebSocketWriteHandlers = registerWebSocketWriteHandlers;
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    NetServer s;
    synchronized (this) {
      s = tcpServer;
    }
    if (s == null) {
      throw new IllegalStateException("Not listening");
    }
    options = options.copy();
    return s.updateSSLOptions(options, force);
  }

  @Override
  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    NetServer s;
    synchronized (this) {
      s = tcpServer;
    }
    if (s == null) {
      throw new IllegalStateException("Not listening");
    }
    return s.updateTrafficShapingOptions(options);
  }

  @Override
  public synchronized int actualPort() {
    NetServer s = tcpServer;
    return s != null ? s.actualPort() : 0;
  }

  @Override
  public Metrics getMetrics() {
    NetServerImpl s;
    synchronized (this) {
      s = (NetServerImpl) tcpServer;
    }
    return s == null ? null : s.getMetrics();
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
    webSocketHandler = handler;
    return this;
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    if (isListening()) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    webSocketHandhakeHandler = handler;
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
    return webSocketHandler;
  }

  @Override
  public Future<HttpServer> listen() {
    return listen(options.getPort(), options.getHost());
  }

  @Override
  public synchronized Future<HttpServer> listen(SocketAddress address) {
    if (requestHandler == null && webSocketHandler == null && webSocketHandhakeHandler == null) {
      throw new IllegalStateException("Set request or WebSocket handler first");
    }
    if (tcpServer != null) {
      throw new IllegalStateException();
    }
    HttpOverTcpServerConfig options = this.options;
    ContextInternal context = vertx.getOrCreateContext();
    ContextInternal listenContext;
    // Not sure of this
    if (context.isEventLoopContext()) {
      listenContext = context;
    } else {
      listenContext = context.toBuilder()
        .withThreadingModel(ThreadingModel.EVENT_LOOP)
        .build();
    }
    NetServerInternal server = new NetServerBuilder(vertx, options.getEndpointConfig())
      .fileRegionEnabled(!options.getCompression().isCompressionEnabled())
      .metricsProvider((metrics, addr) -> metrics.createHttpServerMetrics(new HttpServerOptions(), addr))
      .build();
    Handler<Throwable> h = exceptionHandler;
    Handler<Throwable> exceptionHandler = h != null ? h : DEFAULT_EXCEPTION_HANDLER;
    server.exceptionHandler(exceptionHandler);
    server.connectHandler(so -> {
      NetSocketImpl soi = (NetSocketImpl) so;
      Supplier<ContextInternal> streamContextSupplier = context::duplicate;
      String host = address.isInetSocket() ? address.host() : "localhost";
      int port = address.port();
      String serverOrigin = (options.isSsl() ? "https" : "http") + "://" + host + ":" + port;
      HttpServerConnectionHandler handler = new HttpServerConnectionHandler(
        this,
        serverOrigin,
        requestHandler,
        invalidRequestHandler,
        webSocketHandler,
        webSocketHandhakeHandler,
        connectionHandler,
        exceptionHandler,
        options.getHttp2Config().getConnectionWindowSize());
      HttpServerConnectionInitializer initializer = new HttpServerConnectionInitializer(
        listenContext,
        context.threadingModel(),
        options.getStrictThreadMode() && context.threadingModel() == ThreadingModel.EVENT_LOOP,
        streamContextSupplier,
        this,
        options.getCompression().isCompressionEnabled(), // Todo : remove
        options.getCompression().isDecompressionEnabled(), // Todo : remove
        options.getTracingPolicy(),
        options.getEndpointConfig().getNetworkLogging() != null,
        options.getCompression().getCompressors().toArray(new CompressionOptions[0]),
        options.getCompression().getContentSizeThreshold(),
        options.isHandle100ContinueAutomatically(),
        options.getMaxFormAttributeSize(),
        options.getMaxFormFields(),
        options.getMaxFormBufferedBytes(),
        options.getHttp1Config(),
        options.getHttp2Config(),
        registerWebSocketWriteHandlers,
        options.getWebSocketConfig(),
        options.isSsl() ? options.getSslOptions() : null,
        serverOrigin,
        handler,
        exceptionHandler,
        soi.metric());
      initializer.configurePipeline(soi.channel(), null, null);
    });
    tcpServer = server;
    closeSequence = new CloseSequence(p -> doClose(server, p), p -> doShutdown(server, p ));
    Promise<HttpServer> result = context.promise();
    tcpServer.listen(listenContext, address).onComplete(ar -> {
      if (ar.succeeded()) {
        result.complete(this);
      } else {
        result.fail(ar.cause());
      }
    });
    return result.future();
  }

  private void doShutdown(NetServer netServer, Completable<Void> p) {
    netServer.shutdown(closeTimeout, closeTimeoutUnit).onComplete(p);
  }

  private void doClose(NetServer netServer, Completable<Void> p) {
    if (requestHandler instanceof Closeable) {
      Closeable closeable = (Closeable) requestHandler;
      closeable.close((res, err) -> {
        netServer.close().onComplete(p);
      });
    } else {
      netServer.close().onComplete(p);
    }
  }

  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    CloseSequence seq;
    synchronized (this) {
      seq = closeSequence;
      closeTimeout = timeout;
      closeTimeoutUnit = unit;
      closeSequence = null;
    }
    ContextInternal ctx = vertx.getOrCreateContext();
    if (seq == null) {
      return ctx.succeededFuture();
    } else {
      Promise<Void> p = ctx.promise();
      seq.close().onComplete(p);
      return p.future();
    }
  }

  private boolean isListening() {
    return tcpServer != null;
  }

  public synchronized boolean isClosed() {
    NetServerImpl s = (NetServerImpl) tcpServer;
    return s == null || s.isClosed();
  }

  boolean requestAccept() {
    // Might be useful later
    return true;
  }
}
