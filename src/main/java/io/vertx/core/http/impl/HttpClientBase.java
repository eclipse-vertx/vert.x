/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.ProxyFilter;
import io.vertx.core.net.impl.pool.*;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class is thread-safe.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientBase implements MetricsProvider, Closeable {

  protected final VertxInternal vertx;
  protected final HttpClientOptions options;
  private final ConnectionManager<EndpointKey, HttpClientConnection> webSocketCM;
  protected final NetClientImpl netClient;
  protected final HttpClientMetrics metrics;
  private final boolean keepAlive;
  private final boolean pipelining;
  protected final CloseFuture closeFuture;
  private Predicate<SocketAddress> proxyFilter;
  private final Function<ContextInternal, ContextInternal> contextProvider;

  public HttpClientBase(VertxInternal vertx, HttpClientOptions options, CloseFuture closeFuture) {
    this.vertx = vertx;
    this.metrics = vertx.metricsSPI() != null ? vertx.metricsSPI().createHttpClientMetrics(options) : null;
    this.options = new HttpClientOptions(options);
    this.closeFuture = closeFuture;
    List<HttpVersion> alpnVersions = options.getAlpnVersions();
    if (alpnVersions == null || alpnVersions.isEmpty()) {
      switch (options.getProtocolVersion()) {
        case HTTP_2:
          alpnVersions = Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);
          break;
        default:
          alpnVersions = Collections.singletonList(options.getProtocolVersion());
          break;
      }
    }
    this.keepAlive = options.isKeepAlive();
    this.pipelining = options.isPipelining();
    if (!keepAlive && pipelining) {
      throw new IllegalStateException("Cannot have pipelining with no keep alive");
    }
    this.proxyFilter = options.getNonProxyHosts() != null ? ProxyFilter.nonProxyHosts(options.getNonProxyHosts()) : ProxyFilter.DEFAULT_PROXY_FILTER;
    this.netClient = (NetClientImpl) new NetClientBuilder(vertx, new NetClientOptions(options)
      .setHostnameVerificationAlgorithm(options.isVerifyHost() ? "HTTPS": "")
      .setProxyOptions(null)
      .setApplicationLayerProtocols(alpnVersions
        .stream()
        .map(HttpVersion::alpnName)
        .collect(Collectors.toList())))
      .metrics(metrics)
      .closeFuture(closeFuture)
      .build();
    webSocketCM = webSocketConnectionManager();
    int eventLoopSize = options.getPoolEventLoopSize();
    if (eventLoopSize > 0) {
      ContextInternal[] eventLoops = new ContextInternal[eventLoopSize];
      for (int i = 0;i < eventLoopSize;i++) {
        eventLoops[i] = vertx.createEventLoopContext();
      }
      AtomicInteger idx = new AtomicInteger();
      contextProvider = ctx -> {
        int i = idx.getAndIncrement();
        return eventLoops[i % eventLoopSize];
      };
    } else {
      contextProvider = ConnectionPool.EVENT_LOOP_CONTEXT_PROVIDER;
    }

    closeFuture.add(netClient);
  }

  public NetClient netClient() {
    return netClient;
  }

  private ConnectionManager<EndpointKey, HttpClientConnection> webSocketConnectionManager() {
    return new ConnectionManager<>();
  }

  Function<ContextInternal, ContextInternal> contextProvider() {
    return contextProvider;
  }

  protected final int getPort(RequestOptions request) {
    Integer port = request.getPort();
    if (port != null) {
      return port;
    }
    SocketAddress server = request.getServer();
    if (server != null && server.isInetSocket()) {
      return server.port();
    }
    return options.getDefaultPort();
  }

  private ProxyOptions getProxyOptions(ProxyOptions proxyOptions) {
    if (proxyOptions == null) {
      proxyOptions = options.getProxyOptions();
    }
    return proxyOptions;
  }

  protected final String getHost(RequestOptions request) {
    String host = request.getHost();
    if (host != null) {
      return host;
    }
    SocketAddress server = request.getServer();
    if (server != null && server.isInetSocket()) {
      return server.host();
    }
    return options.getDefaultHost();
  }

  protected final ProxyOptions resolveProxyOptions(ProxyOptions proxyOptions, SocketAddress addr) {
    proxyOptions = getProxyOptions(proxyOptions);
    if (proxyFilter != null) {
      if (!proxyFilter.test(addr)) {
        proxyOptions = null;
      }
    }
    return proxyOptions;
  }

  HttpClientMetrics metrics() {
    return metrics;
  }

  public void webSocket(WebSocketConnectOptions connectOptions, Handler<AsyncResult<WebSocket>> handler) {
    PromiseInternal<WebSocket> promise = vertx.promise();
    promise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        ar.result().resume();
      }
      handler.handle(ar);
    });
    webSocket(connectOptions, promise);
  }

  Future<WebSocket> webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions) {
    PromiseInternal<WebSocket> promise = ctx.promise();
    webSocket(connectOptions, promise);
    return promise.andThen(ar -> {
      if (ar.succeeded()) {
        ar.result().resume();
      }
    });
  }

  private void webSocket(WebSocketConnectOptions connectOptions, PromiseInternal<WebSocket> promise) {
    ContextInternal ctx = promise.context();
    int port = getPort(connectOptions);
    String host = getHost(connectOptions);
    SocketAddress addr = SocketAddress.inetSocketAddress(port, host);
    ProxyOptions proxyOptions = resolveProxyOptions(connectOptions.getProxyOptions(), addr);
    EndpointKey key = new EndpointKey(connectOptions.isSsl() != null ? connectOptions.isSsl() : options.isSsl(), proxyOptions, addr, addr);
    ContextInternal eventLoopContext;
    if (ctx.isEventLoopContext()) {
      eventLoopContext = ctx;
    } else {
      eventLoopContext = vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    EndpointProvider<HttpClientConnection> provider = new EndpointProvider<HttpClientConnection>() {
      @Override
      public Endpoint<HttpClientConnection> create(ContextInternal ctx, Runnable dispose) {
        int maxPoolSize = options.getMaxWebSockets();
        ClientMetrics metrics = HttpClientBase.this.metrics != null ? HttpClientBase.this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
        HttpChannelConnector connector = new HttpChannelConnector(HttpClientBase.this, netClient, proxyOptions, metrics, HttpVersion.HTTP_1_1, key.ssl, false, key.peerAddr, key.serverAddr);
        return new WebSocketEndpoint(null, maxPoolSize, connector, dispose);
      }
    };
    webSocketCM.getConnection(
      eventLoopContext,
      key,
      provider,
      ar -> {
        if (ar.succeeded()) {
          Http1xClientConnection conn = (Http1xClientConnection) ar.result();
          long timeout = Math.max(connectOptions.getTimeout(), 0L);
          if (connectOptions.getIdleTimeout() >= 0L) {
            timeout = connectOptions.getIdleTimeout();
          }
          conn.toWebSocket(ctx,
            connectOptions.getURI(),
            connectOptions.getHeaders(),
            connectOptions.getAllowOriginHeader(),
            connectOptions.getVersion(),
            connectOptions.getSubProtocols(),
            timeout,
            connectOptions.isRegisterWriteHandlers(),
            HttpClientBase.this.options.getMaxWebSocketFrameSize(),
            promise);
        } else {
          promise.fail(ar.cause());
        }
      });
  }

//  @Override
  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(port, host, requestURI, promise);
    return promise.future();
  }

//  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(host, requestURI, promise);
    return promise.future();
  }

//  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(requestURI, promise);
    return promise.future();
  }

//  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(options, promise);
    return promise.future();
  }

//  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    Promise<WebSocket> promise = vertx.promise();
    webSocketAbs(url, headers, version, subProtocols, promise);
    return promise.future();
  }

//  @Override
  public void webSocket(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(new WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port), handler);
  }

//  @Override
  public void webSocket(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(options.getDefaultPort(), host, requestURI, handler);
  }

//  @Override
  public void webSocket(String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, handler);
  }

//  @Override
  public void webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols, Handler<AsyncResult<WebSocket>> handler) {
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setAbsoluteURI(url)
      .setHeaders(headers)
      .setVersion(version)
      .setSubProtocols(subProtocols);
    webSocket(options, handler);
  }

  @Override
  public void close(Promise<Void> completion) {
    webSocketCM.close();
    completion.complete();
  }

//  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    netClient.close(handler);
  }

//  @Override
  public Future<Void> close() {
    return netClient.close();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getMetrics() != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

//  @Override
  public Future<Void> updateSSLOptions(SSLOptions options) {
    return netClient.updateSSLOptions(options);
  }

  public HttpClientBase proxyFilter(Predicate<SocketAddress> filter) {
    proxyFilter = filter;
    return this;
  }

//  @Override
  public HttpClientOptions options() {
    return options;
  }

//  @Override
  public VertxInternal vertx() {
    return vertx;
  }

}
