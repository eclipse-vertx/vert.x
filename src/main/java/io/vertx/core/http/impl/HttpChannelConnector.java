/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.http.impl.pool.ConnectionProvider;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ChannelProvider;
import io.vertx.core.net.impl.ProxyChannelProvider;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import javax.net.ssl.SSLHandshakeException;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
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
  private final long http2MaxConcurrency;

  HttpChannelConnector(HttpClientImpl client) {
    this.client = client;
    this.options = client.getOptions();
    this.metrics = client.metrics();
    this.sslHelper = client.getSslHelper();
    this.version = options.getProtocolVersion();
    this.http1Weight = client.getOptions().getHttp2MaxPoolSize();
    this.http2Weight = client.getOptions().getMaxPoolSize();
    this.weight = version == HttpVersion.HTTP_2 ? http2Weight : http1Weight;
    this.http2MaxConcurrency = client.getOptions().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.getOptions().getHttp2MultiplexingLimit();
  }

  @Override
  public boolean isValid(HttpClientConnection conn) {
    return conn.isValid();
  }

  @Override
  public void close(HttpClientConnection conn) {
    conn.close();
  }

  public long connect(
    ConnectionListener<HttpClientConnection> listener,
    Object endpointMetric,
    ContextImpl context,
    String peerHost,
    boolean ssl,
    String host,
    int port) {

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
                http2Connected(listener, endpointMetric, context, ch);
              } else {
                applyHttp1xConnectionOptions(ch.pipeline());
                HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
                  HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
                http1xConnected(listener, fallbackProtocol, host, port, true, endpointMetric, context, ch);
              }
            } else {
              applyHttp1xConnectionOptions(ch.pipeline());
              http1xConnected(listener, version, host, port, true, endpointMetric, context, ch);
            }
          } else {
            handshakeFailure(ch, fut.cause(), listener);
          }
        });
      } else {
        if (version == HttpVersion.HTTP_2) {
          if (client.getOptions().isHttp2ClearTextUpgrade()) {
            HttpClientCodec httpCodec = new HttpClientCodec();
            class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
              @Override
              public void channelActive(ChannelHandlerContext ctx) throws Exception {
                DefaultFullHttpRequest upgradeRequest =
                    new DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                String hostHeader = peerHost;
                if (port != 80) {
                  hostHeader += ":" + port;
                }
                upgradeRequest.headers().set(HttpHeaderNames.HOST, hostHeader);
                ctx.writeAndFlush(upgradeRequest);
                ctx.fireChannelActive();
              }
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof LastHttpContent) {
                  ChannelPipeline p = ctx.pipeline();
                  p.remove(httpCodec);
                  p.remove(this);
                  // Upgrade handler will remove itself
                  applyHttp1xConnectionOptions(ch.pipeline());
                  http1xConnected(listener, HttpVersion.HTTP_1_1, host, port, false, endpointMetric, context, ch);
                }
              }
              @Override
              public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                super.userEventTriggered(ctx, evt);
                if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                  ctx.pipeline().remove(this);
                  // Upgrade handler will remove itself
                }
              }
            }
            VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(client.getOptions().getInitialSettings()) {
              @Override
              public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {
                applyHttp2ConnectionOptions(pipeline);
                http2Connected(listener, endpointMetric, context, ch);
              }
            };
            HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536);
            ch.pipeline().addLast(httpCodec, upgradeHandler, new UpgradeRequestHandler());
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
          if (ch.pipeline().get(HttpClientUpgradeHandler.class) != null) {
            // Upgrade handler do nothing
          } else {
            if (version == HttpVersion.HTTP_2 && !client.getOptions().isHttp2ClearTextUpgrade()) {
              http2Connected(listener, endpointMetric, context, ch);
            } else {
              http1xConnected(listener, version, host, port, false, endpointMetric, context, ch);
            }
          }
        }
      } else {
        connectFailed(null, listener, res.cause());
      }
    };

    channelProvider.connect(client.getVertx(), bootstrap, client.getOptions().getProxyOptions(), SocketAddress.inetSocketAddress(port, host), channelInitializer, channelHandler);

    return weight;
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

  private void handshakeFailure(Channel ch, Throwable cause, ConnectionListener<HttpClientConnection> listener) {
    SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
    if (cause != null) {
      sslException.initCause(cause);
    }
    connectFailed(ch, listener, sslException);
  }

  private void http1xConnected(ConnectionListener<HttpClientConnection> listener,
                               HttpVersion version,
                               String host,
                               int port,
                               boolean ssl,
                               Object endpointMetric,
                               ContextImpl context,
                               Channel ch) {
    Http1xClientHandler clientHandler = new Http1xClientHandler(
      listener,
      context,
      version,
      host,
      port,
      ssl,
      client,
      endpointMetric,
      client.metrics());
    clientHandler.addHandler(conn -> {
      listener.onConnectSuccess(conn, 1, ch, context, weight, http1Weight, Long.MAX_VALUE);
    });
    clientHandler.removeHandler(conn -> {
      listener.onClose(conn);
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  private void http2Connected(ConnectionListener<HttpClientConnection> listener,
                              Object endpointMetric,
                              ContextImpl context,
                              Channel ch) {
    try {
      boolean upgrade;
      upgrade = ch.pipeline().get(SslHandler.class) == null && options.isHttp2ClearTextUpgrade();
      VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>(ch)
        .server(false)
        .clientUpgrade(upgrade)
        .useCompression(client.getOptions().isTryUseCompression())
        .initialSettings(client.getOptions().getInitialSettings())
        .connectionFactory(connHandler -> new Http2ClientConnection(listener, endpointMetric, client, context, connHandler, metrics))
        .logEnabled(options.getLogActivity())
        .build();
      handler.addHandler(conn -> {
        if (options.getHttp2ConnectionWindowSize() > 0) {
          conn.setWindowSize(options.getHttp2ConnectionWindowSize());
        }
        if (metrics != null) {
          Object metric = metrics.connected(conn.remoteAddress(), conn.remoteName());
          conn.metric(metric);
        }
        long concurrency = conn.remoteSettings().getMaxConcurrentStreams();
        listener.onConnectSuccess(conn, concurrency, ch, context, weight, http2Weight, http2MaxConcurrency);
      });
      handler.removeHandler(conn -> {
        if (metrics != null) {
          metrics.endpointDisconnected(endpointMetric, conn.metric());
        }
        listener.onClose(conn);
      });
    } catch (Exception e) {
      connectFailed(ch, listener, e);
    }
  }

  private void connectFailed(Channel ch, ConnectionListener<HttpClientConnection> listener, Throwable t) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    listener.onConnectFailure(t, weight);
  }
}
