/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.internal.resource.ResourceManager;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Function;

public class WebSocketClientImpl extends HttpClientBase implements WebSocketClient {

  private final WebSocketClientOptions options;
  private final ResourceManager<EndpointKey, WebSocketGroup> webSocketCM;

  public WebSocketClientImpl(VertxInternal vertx, HttpClientOptions options, WebSocketClientOptions wsOptions) {
    super(vertx, options);

    this.options = wsOptions;
    this.webSocketCM = new ResourceManager<>();
  }

  protected void doShutdown(Promise<Void> p) {
    webSocketCM.shutdown();
    super.doShutdown(p);
  }

  protected void doClose(Promise<Void> p) {
    webSocketCM.close();
    super.doClose(p);
  }

  @Override
  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return webSocket(options);
  }

  void webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions, Promise<WebSocket> promise) {
    int port = getPort(connectOptions);
    String host = getHost(connectOptions);
    SocketAddress addr = SocketAddress.inetSocketAddress(port, host);
    HostAndPort peer = HostAndPort.create(host, port);
    ProxyOptions proxyOptions = computeProxyOptions(connectOptions.getProxyOptions(), addr);
    ClientSSLOptions sslOptions = sslOptions(connectOptions);
    EndpointKey key = new EndpointKey(connectOptions.isSsl() != null ? connectOptions.isSsl() : options.isSsl(), sslOptions, proxyOptions, addr, peer);
    // todo: cache
    Function<EndpointKey, WebSocketGroup> provider = (key_) -> {
      int maxPoolSize = options.getMaxConnections();
      ClientMetrics clientMetrics = WebSocketClientImpl.this.metrics != null ? WebSocketClientImpl.this.metrics.createEndpointMetrics(key_.server, maxPoolSize) : null;
      PoolMetrics queueMetrics = WebSocketClientImpl.this.metrics != null ? vertx.metrics().createPoolMetrics("ws", key_.server.toString(), maxPoolSize) : null;
      HttpChannelConnector connector = new HttpChannelConnector(WebSocketClientImpl.this, netClient, sslOptions, key_.proxyOptions, clientMetrics, HttpVersion.HTTP_1_1, key_.ssl, false, key_.authority, key_.server, false);
      return new WebSocketGroup(null, queueMetrics, options, maxPoolSize, connector);
    };
    webSocketCM
      .withResourceAsync(key, provider, (endpoint, created) -> endpoint.requestConnection(ctx, connectOptions, 0L))
      .onComplete(c -> {
        if (c.succeeded()) {
          WebSocket conn = c.result();
          promise.complete(conn);
        } else {
          promise.fail(c.cause());
        }
      });
  }

  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    return webSocket(new WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port));
  }

  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    return webSocket(vertx.getOrCreateContext(), options);
  }

  static WebSocketConnectOptions webSocketConnectOptionsAbs(String url, MultiMap headers, WebSocketVersion version, List<String> subProtocols) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
    String scheme = uri.getScheme();
    if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
      throw new IllegalArgumentException("Scheme: " + scheme);
    }
    boolean ssl = scheme.length() == 3;
    int port = uri.getPort();
    if (port == -1) port = ssl ? 443 : 80;
    StringBuilder relativeUri = new StringBuilder();
    if (uri.getRawPath() != null) {
      relativeUri.append(uri.getRawPath());
    }
    if (uri.getRawQuery() != null) {
      relativeUri.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      relativeUri.append('#').append(uri.getRawFragment());
    }
    return new WebSocketConnectOptions()
      .setHost(uri.getHost())
      .setPort(port).setSsl(ssl)
      .setURI(relativeUri.toString())
      .setHeaders(headers)
      .setVersion(version)
      .setSubProtocols(subProtocols);
  }

  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebSocketVersion version, List<String> subProtocols) {
    return webSocket(webSocketConnectOptionsAbs(url, headers, version, subProtocols));
  }

  Future<WebSocket> webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions) {
    PromiseInternal<WebSocket> promise = ctx.promise();
    webSocket(ctx, connectOptions, promise);
    return promise.andThen(ar -> {
      if (ar.succeeded()) {
        ar.result().resume();
      }
    });
  }

  public ClientWebSocket webSocket() {
    return new ClientWebSocketImpl(this);
  }
}
