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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.impl.clientconnection.ConnectResult;
import io.vertx.core.net.impl.clientconnection.ConnectionListener;
import io.vertx.core.net.impl.clientconnection.ConnectionProvider;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ChannelProvider;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpChannelConnector implements ConnectionProvider<HttpClientConnection> {

  private final HttpClientImpl client;
  private final ChannelGroup channelGroup;
  private final ContextInternal context;
  private final HttpClientOptions options;
  private final ClientMetrics metrics;
  private final SSLHelper sslHelper;
  private final HttpVersion version;
  private final long weight;
  private final long http1Weight;
  private final long http2Weight;
  private final long http1MaxConcurrency;
  private final boolean ssl;
  private final SocketAddress peerAddress;
  private final SocketAddress server;

  public HttpChannelConnector(HttpClientImpl client,
                              ChannelGroup channelGroup,
                              ContextInternal context,
                              ClientMetrics metrics,
                              HttpVersion version,
                              boolean ssl,
                              SocketAddress peerAddress,
                              SocketAddress server) {
    this.client = client;
    this.channelGroup = channelGroup;
    this.context = context;
    this.metrics = metrics;
    this.options = client.getOptions();
    this.sslHelper = client.getSslHelper();
    this.version = version;
    // this is actually normal (although it sounds weird)
    // the pool uses a weight mechanism to keep track of the max number of connections
    // for instance when http2Size = 2 and http1Size= 5 then maxWeight = 10
    // which means that the pool can contain
    // - maxWeight / http1Weight = 5 HTTP/1.1 connections
    // - maxWeight / http2Weight = 2 HTTP/2 connections
    this.http1Weight = options.getHttp2MaxPoolSize();
    this.http2Weight = options.getMaxPoolSize();
    this.weight = version == HttpVersion.HTTP_2 ? http2Weight : http1Weight;
    this.http1MaxConcurrency = options.isPipelining() ? options.getPipeliningLimit() : 1;
    this.ssl = ssl;
    this.peerAddress = peerAddress;
    this.server = server;
  }

  public long weight() {
    return weight;
  }

  @Override
  public void close(HttpClientConnection conn) {
    conn.close();
  }

  @Override
  public void init(HttpClientConnection conn) {
    Handler<HttpConnection> handler = client.connectionHandler();
    if (handler != null) {
      context.dispatch(conn, handler);
    }
  }

  @Override
  public boolean isValid(HttpClientConnection conn) {
    return conn.isValid();
  }

  @Override
  public void connect(ConnectionListener<HttpClientConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<HttpClientConnection>>> handler) {
    Promise<ConnectResult<HttpClientConnection>> promise = Promise.promise();
    promise.future().onComplete(handler);
    try {
      doConnect(listener, context, promise);
    } catch(Exception e) {
      promise.tryFail(e);
    }
  }

  private void doConnect(
    ConnectionListener<HttpClientConnection> listener,
    ContextInternal context,
    Promise<ConnectResult<HttpClientConnection>> future) {

    boolean domainSocket = server.path() != null;
    boolean useAlpn = options.isUseAlpn();

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.nettyEventLoop());

    applyConnectionOptions(domainSocket, bootstrap);

    ProxyOptions options = this.options.getProxyOptions();
    if (options != null && !ssl && options.getType()== ProxyType.HTTP) {
      // http proxy requests are handled in HttpClientImpl, everything else can use netty proxy handler
      options = null;
    }
    ChannelProvider channelProvider = new ChannelProvider(bootstrap, sslHelper, context, options);
    // SocketAddress.inetSocketAddress(server.port(), peerHost)
    Future<Channel> fut = channelProvider.connect(server, peerAddress, this.options.isForceSni() ? peerAddress.host() : null, ssl);

    fut.addListener((GenericFutureListener<Future<Channel>>) res -> {
      if (res.isSuccess()) {
        Channel ch = res.getNow();
        channelGroup.add(ch);
        if (ssl) {
          String protocol = channelProvider.applicationProtocol();
          if (useAlpn) {
            if ("h2".equals(protocol)) {
              applyHttp2ConnectionOptions(ch.pipeline());
              http2Connected(listener, context, ch, future);
            } else {
              applyHttp1xConnectionOptions(ch.pipeline());
              HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
                HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
              http1xConnected(listener, fallbackProtocol, server, true, context, ch, http1Weight, future);
            }
          } else {
            applyHttp1xConnectionOptions(ch.pipeline());
            http1xConnected(listener, version, server, true, context, ch, http1Weight, future);
          }
        } else {
          ChannelPipeline pipeline = ch.pipeline();
          if (version == HttpVersion.HTTP_2) {
            if (this.options.isHttp2ClearTextUpgrade()) {
              applyHttp1xConnectionOptions(pipeline);
              http1xConnected(listener, version, server, false, context, ch, http2Weight, future);
            } else {
              applyHttp2ConnectionOptions(pipeline);
              http2Connected(listener, context, ch, future);
            }
          } else {
            applyHttp1xConnectionOptions(pipeline);
            http1xConnected(listener, version, server, false, context, ch, http1Weight, future);
          }
        }
      } else {
        connectFailed(channelProvider.channel(), listener, res.cause(), future);
      }
    });
  }

  private void applyConnectionOptions(boolean domainSocket, Bootstrap bootstrap) {
    client.getVertx().transport().configure(options, domainSocket, bootstrap);
  }

  private void applyHttp2ConnectionOptions(ChannelPipeline pipeline) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
  }

  private void applyHttp1xConnectionOptions(ChannelPipeline pipeline) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
    if (options.getLogActivity()) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    pipeline.addLast("codec", new HttpClientCodec(
      options.getMaxInitialLineLength(),
      options.getMaxHeaderSize(),
      options.getMaxChunkSize(),
      false,
      false,
      options.getDecoderInitialBufferSize()));
    if (options.isTryUseCompression()) {
      pipeline.addLast("inflater", new HttpContentDecompressor(false));
    }
  }

  private void http1xConnected(ConnectionListener<HttpClientConnection> listener,
                               HttpVersion version,
                               SocketAddress server,
                               boolean ssl,
                               ContextInternal context,
                               Channel ch, long weight,
                               Promise<ConnectResult<HttpClientConnection>> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && options.isHttp2ClearTextUpgrade();
    VertxHandler<Http1xClientConnection> clientHandler = VertxHandler.create(chctx -> {
      HttpClientMetrics met = client.metrics();
      Http1xClientConnection conn = new Http1xClientConnection(listener, upgrade ? HttpVersion.HTTP_1_1 : version, client, chctx, ssl, server, context, this.metrics);
      if (met != null) {
        Object socketMetric = met.connected(conn.remoteAddress(), conn.remoteName());
        conn.metric(socketMetric);
        met.endpointConnected(metrics);
      }
      return conn;
    });
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        future.complete(new ConnectResult<>(new Http2UpgradedClientConnection(client, conn), 1, http2Weight));
      } else {
        future.complete(new ConnectResult<>(conn, http1MaxConcurrency, http1Weight));
      }
    });
    clientHandler.removeHandler(conn -> {
      listener.onEvict();
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  private void http2Connected(ConnectionListener<HttpClientConnection> listener,
                              ContextInternal context,
                              Channel ch,
                              Promise<ConnectResult<HttpClientConnection>> future) {
    try {
      VertxHttp2ConnectionHandler<Http2ClientConnection> clientHandler = Http2ClientConnection.createHttp2ConnectionHandler(client, metrics, listener, context, null, (conn, concurrency) -> {
        future.complete(new ConnectResult<>(conn, concurrency, http2Weight));
      });
      ch.pipeline().addLast("handler", clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, listener, e, future);
    }
  }

  private void connectFailed(Channel ch, ConnectionListener<HttpClientConnection> listener, Throwable t, Promise<ConnectResult<HttpClientConnection>> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }
}
