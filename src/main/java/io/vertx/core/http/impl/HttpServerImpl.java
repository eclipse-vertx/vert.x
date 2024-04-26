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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseSequence;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl implements HttpServer, MetricsProvider {

  static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);

  private static final Handler<Throwable> DEFAULT_EXCEPTION_HANDLER = t -> log.trace("Connection failure", t);

  private static final String DISABLE_WEBSOCKETS_PROP_NAME = "vertx.disableWebsockets";

  static final boolean DISABLE_WEBSOCKETS = Boolean.getBoolean(DISABLE_WEBSOCKETS_PROP_NAME);

  private final VertxInternal vertx;
  final HttpServerOptions options;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> webSocketHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;
  private Handler<HttpConnection> connectionHandler;
  private Handler<Throwable> exceptionHandler;
  private NetServerInternal tcpServer;
  private long closeTimeout = 0L;
  private TimeUnit closeTimeoutUnit = TimeUnit.SECONDS;
  private final CloseSequence closeSequence;

  public HttpServerImpl(VertxInternal vertx, HttpServerOptions options) {
    this.vertx = vertx;
    this.options = options;
    this.closeSequence = new CloseSequence(this::doClose, this::doShutdown);
  }

  public synchronized NetServerInternal tcpServer() {
    return tcpServer;
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
    configureApplicationLayerProtocols(options);
    return s.updateSSLOptions(options, force);
  }

  @Override
  public void updateTrafficShapingOptions(TrafficShapingOptions options) {
    NetServer s;
    synchronized (this) {
      s = tcpServer;
    }
    if (s == null) {
      throw new IllegalStateException("Not listening");
    }
    s.updateTrafficShapingOptions(options);
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
    if (requestHandler == null && webSocketHandler == null) {
      throw new IllegalStateException("Set request or WebSocket handler first");
    }
    if (tcpServer != null) {
      throw new IllegalStateException();
    }
    HttpServerOptions options = this.options;
    HttpServerOptions tcpOptions = new HttpServerOptions(options);
    if (tcpOptions.getSslOptions() != null) {
      configureApplicationLayerProtocols(tcpOptions.getSslOptions());
    }
    ContextInternal context = vertx.getOrCreateContext();
    ContextInternal listenContext;
    if (context.isEventLoopContext()) {
      listenContext = context;
    } else {
      listenContext = vertx.createEventLoopContext(context.nettyEventLoop(), context.workerPool(), context.classLoader());
    }
    NetServerInternal server = vertx.createNetServer(tcpOptions);
    Handler<Throwable> h = exceptionHandler;
    Handler<Throwable> exceptionHandler = h != null ? h : DEFAULT_EXCEPTION_HANDLER;
    server.exceptionHandler(exceptionHandler);
    server.connectHandler(so -> {
      NetSocketImpl soi = (NetSocketImpl) so;
      Supplier<ContextInternal> streamContextSupplier = context::duplicate;
      String host = address.isInetSocket() ? address.host() : "localhost";
      int port = address.port();
      String serverOrigin = (tcpOptions.isSsl() ? "https" : "http") + "://" + host + ":" + port;
      HttpServerConnectionHandler handler = new HttpServerConnectionHandler(
        this,
        requestHandler,
        invalidRequestHandler,
        webSocketHandler,
        connectionHandler,
        exceptionHandler);
      HttpServerConnectionInitializer initializer = new HttpServerConnectionInitializer(
        listenContext,
        streamContextSupplier,
        this,
        vertx,
        options,
        serverOrigin,
        handler,
        exceptionHandler,
        soi.metric());
      initializer.configurePipeline(soi.channel(), null, null);
    });
    tcpServer = server;
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

  protected void doShutdown(Promise<Void> p) {
    tcpServer.shutdown(closeTimeout, closeTimeoutUnit).onComplete(p);
  }

  protected void doClose(Promise<Void> p) {
    tcpServer.close().onComplete(p);
  }

  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    this.closeTimeout = timeout;
    this.closeTimeoutUnit = unit;
    return closeSequence.close();
  }

  @Override
  public Future<Void> close() {
    NetServer s;
    synchronized (this) {
      s = tcpServer;
      if (s == null) {
        return vertx.getOrCreateContext().succeededFuture();
      }
      tcpServer = null;
    }
    return s.close();
  }

  /**
   * Configure the {@code options} to match the server configured HTTP versions.
   */
  private void configureApplicationLayerProtocols(ServerSSLOptions options) {
    List<String> applicationProtocols = this.options
      .getAlpnVersions()
      .stream()
      .map(HttpVersion::alpnName)
      .collect(Collectors.toList());
    options.setApplicationLayerProtocols(applicationProtocols);
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

  boolean wsAccept() {
    // Might be useful later
    return true;
  }
}
