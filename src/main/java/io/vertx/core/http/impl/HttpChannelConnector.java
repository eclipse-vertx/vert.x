/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectResult;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.http.impl.pool.ConnectionProvider;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ChannelProvider;
import io.vertx.core.net.impl.ProxyChannelProvider;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import javax.net.ssl.SSLHandshakeException;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpChannelConnector implements ConnectionProvider<HttpClientConnection> {

  private final HttpClientImpl client;
  private final HttpClientOptions options;
  private final HttpClientMetrics metrics;
  private final SSLHelper sslHelper;
  private final HttpVersion version;
  private final long weight;
  private final long http1Weight;
  private final long http2Weight;
  private final long http1MaxConcurrency;
  private final boolean ssl;
  private final String peerHost;
  private final String host;
  private final int port;
  private final Object metric;

  HttpChannelConnector(HttpClientImpl client,
                       Object metric,
                       HttpVersion version,
                       boolean ssl,
                       String peerHost,
                       String host,
                       int port) {
    this.client = client;
    this.metric = metric;
    this.options = client.getOptions();
    this.metrics = client.metrics();
    this.sslHelper = client.getSslHelper();
    this.version = version;
    this.http1Weight = client.getOptions().getHttp2MaxPoolSize();
    this.http2Weight = client.getOptions().getMaxPoolSize();
    this.weight = version == HttpVersion.HTTP_2 ? http2Weight : http1Weight;
    this.http1MaxConcurrency = client.getOptions().isPipelining() ? client.getOptions().getPipeliningLimit() : 1;
    this.ssl = ssl;
    this.peerHost = peerHost;
    this.host = host;
    this.port = port;
  }

  public long weight() {
    return weight;
  }

  @Override
  public void close(HttpClientConnection conn) {
    conn.close();
  }

  @Override
  public void connect(ConnectionListener<HttpClientConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<HttpClientConnection>>> handler) {
    Future<ConnectResult<HttpClientConnection>> future = Future.<ConnectResult<HttpClientConnection>>future().setHandler(handler);
    try {
      doConnect(listener, context, future);
    } catch(Exception e) {
      future.tryFail(e);
    }
  }

  private void doConnect(
    ConnectionListener<HttpClientConnection> listener,
    ContextInternal context,
    Future<ConnectResult<HttpClientConnection>> future) {

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.nettyEventLoop());
    bootstrap.channel(client.getVertx().transport().channelType(false));

    applyConnectionOptions(bootstrap);

    ChannelProvider channelProvider;
    // http proxy requests are handled in HttpClientImpl, everything else can use netty proxy handler
    if (options.getProxyOptions() == null || !ssl && options.getProxyOptions().getType()== ProxyType.HTTP ) {
      channelProvider = ChannelProvider.INSTANCE;
    } else {
      channelProvider = ProxyChannelProvider.INSTANCE;
    }

    boolean useAlpn = options.isUseAlpn();
    Handler<Channel> channelInitializer = ch -> {

      // Configure pipeline
      ChannelPipeline pipeline = ch.pipeline();
      if (ssl) {
        SslHandler sslHandler = new SslHandler(sslHelper.createEngine(client.getVertx(), peerHost, port, options.isForceSni() ? peerHost : null));
        ch.pipeline().addLast("ssl", sslHandler);
        // TCP connected, so now we must do the SSL handshake
        sslHandler.handshakeFuture().addListener(fut -> {
          if (fut.isSuccess()) {
            String protocol = sslHandler.applicationProtocol();
            if (useAlpn) {
              if ("h2".equals(protocol)) {
                applyHttp2ConnectionOptions(ch.pipeline());
                http2Connected(listener, context, ch, future);
              } else {
                applyHttp1xConnectionOptions(ch.pipeline());
                HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
                  HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
                http1xConnected(listener, fallbackProtocol, host, port, true, context, ch, http1Weight, future);
              }
            } else {
              applyHttp1xConnectionOptions(ch.pipeline());
              http1xConnected(listener, version, host, port, true, context, ch, http1Weight, future);
            }
          } else {
            handshakeFailure(ch, fut.cause(), listener, future);
          }
        });
      } else {
        if (version == HttpVersion.HTTP_2) {
          if (client.getOptions().isHttp2ClearTextUpgrade()) {
            applyHttp1xConnectionOptions(pipeline);
          } else {
            applyHttp2ConnectionOptions(pipeline);
          }
        } else {
          applyHttp1xConnectionOptions(pipeline);
        }
      }
    };

    Handler<AsyncResult<Channel>> channelHandler = res -> {

      if (res.succeeded()) {
        Channel ch = res.result();
        if (!ssl) {
          if (version == HttpVersion.HTTP_2) {
            if (client.getOptions().isHttp2ClearTextUpgrade()) {
              http1xConnected(listener, version, host, port, false, context, ch, http2Weight, future);
            } else {
              http2Connected(listener, context, ch, future);
            }
          } else {
            http1xConnected(listener, version, host, port, false, context, ch, http1Weight, future);
          }
        }
      } else {
        connectFailed(null, listener, res.cause(), future);
      }
    };

    channelProvider.connect(client.getVertx(), bootstrap, client.getOptions().getProxyOptions(), SocketAddress.inetSocketAddress(port, host), channelInitializer, channelHandler);
  }

  private void applyConnectionOptions(Bootstrap bootstrap) {
    client.getVertx().transport().configure(options, bootstrap);
  }

  private void applyHttp2ConnectionOptions(ChannelPipeline pipeline) {
    if (client.getOptions().getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
  }

  private void applyHttp1xConnectionOptions(ChannelPipeline pipeline) {
    if (client.getOptions().getLogActivity()) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    pipeline.addLast("codec", new HttpClientCodec(
      client.getOptions().getMaxInitialLineLength(),
      client.getOptions().getMaxHeaderSize(),
      client.getOptions().getMaxChunkSize(),
      false,
      false,
      client.getOptions().getDecoderInitialBufferSize()));
    if (client.getOptions().isTryUseCompression()) {
      pipeline.addLast("inflater", new HttpContentDecompressor(true));
    }
    if (client.getOptions().getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, client.getOptions().getIdleTimeout()));
    }
  }

  private void handshakeFailure(Channel ch, Throwable cause, ConnectionListener<HttpClientConnection> listener, Future<ConnectResult<HttpClientConnection>> future) {
    SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
    if (cause != null) {
      sslException.initCause(cause);
    }
    connectFailed(ch, listener, sslException, future);
  }

  private void http1xConnected(ConnectionListener<HttpClientConnection> listener,
                               HttpVersion version,
                               String host,
                               int port,
                               boolean ssl,
                               ContextInternal context,
                               Channel ch, long weight,
                               Future<ConnectResult<HttpClientConnection>> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && options.isHttp2ClearTextUpgrade();
    Http1xClientHandler clientHandler = new Http1xClientHandler(
      listener,
      context,
      upgrade ? HttpVersion.HTTP_1_1 : version,
      peerHost,
      host,
      port,
      ssl,
      client,
      metric,
      client.metrics());
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        future.complete(new ConnectResult<>(new Http2UpgradedClientConnection(client, conn), 1, ch, context, http2Weight));
      } else {
        future.complete(new ConnectResult<>(conn, http1MaxConcurrency, ch, context, http1Weight));
      }
    });
    clientHandler.removeHandler(conn -> {
      listener.onDiscard();
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  private void http2Connected(ConnectionListener<HttpClientConnection> listener,
                              ContextInternal context,
                              Channel ch,
                              Future<ConnectResult<HttpClientConnection>> future) {
    try {
      VertxHttp2ConnectionHandler<Http2ClientConnection> handler = Http2ClientConnection.createHttp2ConnectionHandler(client, metric, listener, context, (conn, concurrency) -> {
        future.complete(new ConnectResult<>(conn, concurrency, ch, context, http2Weight));
      });
      ch.pipeline().addLast(handler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, listener, e, future);
    }
  }

  private void connectFailed(Channel ch, ConnectionListener<HttpClientConnection> listener, Throwable t, Future<ConnectResult<HttpClientConnection>> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }
}
