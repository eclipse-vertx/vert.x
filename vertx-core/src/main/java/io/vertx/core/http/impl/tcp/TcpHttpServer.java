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

package io.vertx.core.http.impl.tcp;

import io.netty.handler.codec.compression.CompressionOptions;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseSequence;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.SysProps;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.tcp.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * TCP HTTP server.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TcpHttpServer implements HttpServerInternal {

  static final Logger log = LoggerFactory.getLogger(TcpHttpServer.class);

  private static final Handler<Throwable> DEFAULT_EXCEPTION_HANDLER = t -> log.trace("Connection failure", t);

  static final boolean DISABLE_WEBSOCKETS = SysProps.DISABLE_WEBSOCKETS.getBoolean();

  private final VertxInternal vertx;
  final HttpServerConfig config;
  private final boolean registerWebSocketWriteHandlers;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> webSocketHandler;
  private Handler<ServerWebSocketHandshake> webSocketHandhakeHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;
  private Handler<HttpConnection> connectionHandler;
  private Handler<Throwable> exceptionHandler;
  private NetServerInternal tcpServer;
  private Duration closeTimeout = Duration.ZERO;
  private CloseSequence closeSequence;
  private HttpServerMetrics<?, ?> httpMetrics;

  public TcpHttpServer(VertxInternal vertx, HttpServerConfig config, boolean registerWebSocketWriteHandlers) {
    this.vertx = vertx;
    this.config = config;
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
  public HttpServerMetrics<?, ?> getMetrics() {
    return httpMetrics;
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
    return listen(vertx.getOrCreateContext());
  }

  @Override
  public Future<HttpServer> listen(SocketAddress address) {
    return listen(vertx.getOrCreateContext(), address);
  }

  @Override
  public Future<HttpServer> listen(ContextInternal context) {
    return listen(context, SocketAddress.inetSocketAddress(config.getTcpPort(), config.getTcpHost()));
  }

  public synchronized Future<HttpServer> listen(ContextInternal context, SocketAddress address) {
    if (requestHandler == null && webSocketHandler == null && webSocketHandhakeHandler == null) {
      throw new IllegalStateException("Set request or WebSocket handler first");
    }
    if (tcpServer != null) {
      throw new IllegalStateException();
    }
    HttpServerConfig config = this.config;
    ContextInternal listenContext;
    // Not sure of this
    if (context.isEventLoopContext()) {
      listenContext = context;
    } else {
      listenContext = context.toBuilder()
        .withThreadingModel(ThreadingModel.EVENT_LOOP)
        .build();
    }
    HttpCompressionConfig compression = config.getCompression();
    NetServerInternal server = new NetServerBuilder(vertx, config.getTcpConfig(), config.getSslOptions())
      .fileRegionEnabled(!compression.isCompressionEnabled())
      .cleanable(false)
      .build();
    Handler<Throwable> h = exceptionHandler;
    Handler<Throwable> exceptionHandler = h != null ? h : DEFAULT_EXCEPTION_HANDLER;
    server.exceptionHandler(exceptionHandler);
    server.connectHandler(so -> {
      NetSocketImpl soi = (NetSocketImpl) so;
      Supplier<ContextInternal> streamContextSupplier = context::duplicate;
      String host = address.isInetSocket() ? address.host() : "localhost";
      int port = address.port();
      String serverOrigin = (config.isSsl() ? "https" : "http") + "://" + host + ":" + port;
      HttpServerConnectionHandler handler = new HttpServerConnectionHandler(
        this,
        serverOrigin,
        requestHandler,
        invalidRequestHandler,
        webSocketHandler,
        webSocketHandhakeHandler,
        connectionHandler,
        exceptionHandler,
        config.getHttp2Config().getConnectionWindowSize());

      List<CompressionOptions> compressors = compression.getCompressors();
      HttpServerConnectionInitializer initializer = new HttpServerConnectionInitializer(
        listenContext,
        context.threadingModel(),
        config.getStrictThreadMode() && context.threadingModel() == ThreadingModel.EVENT_LOOP,
        streamContextSupplier,
        this,
        compressors != null && !compressors.isEmpty() && compression.isCompressionEnabled(),
        compressors != null && !compressors.isEmpty() && compression.isDecompressionEnabled(),
        config.getTracingPolicy(),
        config.getTcpConfig().getNetworkLogging() != null,
        compressors != null ? compressors.toArray(new CompressionOptions[0]) : null,
        compression.getContentSizeThreshold(),
        config.isHandle100ContinueAutomatically(),
        config.getMaxFormAttributeSize(),
        config.getMaxFormFields(),
        config.getMaxFormBufferedBytes(),
        config.getHttp1Config(),
        config.getHttp2Config(),
        registerWebSocketWriteHandlers,
        config.getWebSocketConfig(),
        config.isSsl() ? config.getSslOptions() : null,
        serverOrigin,
        handler,
        exceptionHandler,
        httpMetrics,
        soi.metrics(),
        soi.metric());
      initializer.configurePipeline(soi.channel(), null, null, ((NetSocketImpl) so).metrics());
    });
    tcpServer = server;
    httpMetrics = vertx.metrics() != null ? vertx.metrics().createHttpServerMetrics(config, address) : null;
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
    netServer.shutdown(closeTimeout).onComplete(p);
  }

  private void doClose(NetServer netServer, Completable<Void> p) {
    if (requestHandler instanceof Closeable) {
      Closeable closeable = (Closeable) requestHandler;
      closeable.close((res, err) -> {
        netServer.close().onComplete(foo(p));
      });
    } else {
      netServer.close().onComplete(foo(p));
    }
  }

  private Completable<Void> foo(Completable<Void> completable) {
    if (httpMetrics != null) {
      Completable<Void> cont = completable;
      completable = (result, failure) -> {
        httpMetrics.close();
        cont.complete(result, failure);
      };
    }
    return completable;
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    CloseSequence seq;
    synchronized (this) {
      seq = closeSequence;
      closeTimeout = timeout;
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
    NetServerInternal s = tcpServer;
    return s == null || s.isClosed();
  }

  public boolean requestAccept() {
    // Might be useful later
    return true;
  }

  public NetServerInternal tcpServer() {
    return tcpServer;
  }
}
