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
package io.vertx.core.http.impl.quic;

import io.netty.handler.codec.http3.Http3;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import io.vertx.core.http.impl.http3.Http3ServerConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.quic.QuicServerImpl;
import io.vertx.core.spi.metrics.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * QUIC HTTP server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicHttpServer implements HttpServerInternal {

  private final VertxInternal vertx;
  private final HttpServerConfig config;
  private final Http3ServerConfig http3Config;
  private final QuicServerConfig quicConfig;
  private volatile Handler<HttpServerRequest> requestHandler;
  private Handler<HttpConnection> connectionHandler;
  private QuicServerImpl quicServer;
  private volatile int actualPort;

  public QuicHttpServer(VertxInternal vertx, HttpServerConfig config) {
    this.vertx = vertx;
    this.config = new HttpServerConfig(config);
    this.http3Config = config.getHttp3Config() != null ? config.getHttp3Config() : new Http3ServerConfig();
    this.quicConfig = config.getQuicConfig() != null ? config.getQuicConfig() : new QuicServerConfig();
    this.actualPort = 0;
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    this.requestHandler = handler;
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    return this;
  }

  @Override
  public HttpServer connectionHandler(Handler<HttpConnection> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    connectionHandler = handler;
    return this;
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    return this;
  }

  @Override
  public HttpServer exceptionHandler(Handler<Throwable> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    return this;
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    return this;
  }

  @Override
  public Handler<ServerWebSocket> webSocketHandler() {
    return null;
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    return vertx.failedFuture("HTTP/3 server options cannot be updated");
  }

  @Override
  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    return vertx.succeededFuture();
  }

  private static class ConnectionHandler implements Handler<QuicConnection> {

    private final io.vertx.core.net.QuicServer transport;
    private final Handler<HttpServerRequest> requestHandler;
    private final Handler<HttpConnection> connectionHandler;
    private final boolean handle100ContinueAutomatically;
    private final int maxFormAttributeSize;
    private final int maxFormFields;
    private final int maxFormBufferedSize;
    private final Http3Settings localSettings;

    public ConnectionHandler(io.vertx.core.net.QuicServer transport,
                             Handler<HttpServerRequest> requestHandler,
                             Handler<HttpConnection> connectionHandler,
                             boolean handle100ContinueAutomatically,
                             int maxFormAttributeSize,
                             int maxFormFields,
                             int maxFormBufferedSize,
                             Http3Settings localSettings) {
      this.transport = transport;
      this.requestHandler = requestHandler;
      this.connectionHandler = connectionHandler;
      this.handle100ContinueAutomatically = handle100ContinueAutomatically;
      this.maxFormAttributeSize = maxFormAttributeSize;
      this.maxFormFields = maxFormFields;
      this.maxFormBufferedSize = maxFormBufferedSize;
      this.localSettings = localSettings;
    }

    @Override
    public void handle(QuicConnection connection) {
      String host = connection.localAddress().host();
      int port = connection.localAddress().port();
      String serverOrigin = "https://" + host + ":" + port;

      QuicConnectionInternal connectionInternal = (QuicConnectionInternal) connection;

      HttpServerMetrics<?, ?, ?> metrics = (HttpServerMetrics<?, ?, ?>)((MetricsProvider)transport).getMetrics();

      Http3ServerConnection http3Connection = new Http3ServerConnection(connectionInternal, localSettings, metrics);

      http3Connection.init();

      http3Connection.streamHandler(stream -> {
        HttpServerRequestImpl request = new HttpServerRequestImpl(requestHandler, stream, stream.context(),
          handle100ContinueAutomatically, maxFormAttributeSize,
          maxFormFields, maxFormBufferedSize, serverOrigin);
        request.init();
      });

      Handler<HttpConnection> handler = connectionHandler;
      if (handler != null) {
        ContextInternal ctx = connectionInternal.context();
        ctx.dispatch(http3Connection, handler);
      }
    }
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
    return listen(context, SocketAddress.inetSocketAddress(config.getQuicPort(), config.getQuicHost()));
  }

  @Override
  public Future<HttpServer> listen(ContextInternal current, SocketAddress address) {

  Handler<HttpServerRequest> requestHandler;
    Handler<HttpConnection> connectionHandler;

    BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider;
    VertxMetrics metrics = vertx.metrics();
    if (metrics != null) {
      metricsProvider = (quicEndpointOptions, socketAddress) -> metrics
        .createHttpServerMetrics(config, socketAddress);
    } else {
      metricsProvider = null;
    }

    ServerSSLOptions sslOptions = config.getSslOptions().copy();
    sslOptions.setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));

    synchronized (this) {
      if (quicServer != null) {
        return current.failedFuture(new IllegalStateException("Already listening on port " + address.port()));
      }
      requestHandler = this.requestHandler;
      connectionHandler = this.connectionHandler;
      quicServer = new QuicServerImpl(vertx, metricsProvider, quicConfig, sslOptions);
    }

    if (requestHandler == null) {
      return current.failedFuture(new IllegalStateException("Set request handler first"));
    }

    quicServer.handler(new ConnectionHandler(quicServer, requestHandler, connectionHandler,
      config.isHandle100ContinueAutomatically(), config.getMaxFormAttributeSize(), config.getMaxFormFields(),
      config.getMaxFormBufferedBytes(), http3Config.getInitialSettings() != null ? http3Config.getInitialSettings().copy() : new Http3Settings()));
    return quicServer
      .bind(current, address)
      .map(port -> {
        actualPort = port;
        return this;
      });
  }

  @Override
  public boolean isClosed() {
    return quicServer == null;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    io.vertx.core.net.QuicServer s;
    synchronized (this) {
      s = quicServer;
      if (s == null) {
        return vertx.getOrCreateContext().succeededFuture();
      }
      quicServer = null;
    }
    return s.shutdown(Duration.ofMillis(unit.toMillis(timeout)));
  }

  @Override
  public int actualPort() {
    return actualPort;
  }

  @Override
  public Metrics getMetrics() {
    QuicServerImpl s;
    synchronized (this) {
      s = (QuicServerImpl) quicServer;
    }
    return s == null ? null : s.getMetrics();
  }
}
