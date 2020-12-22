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
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ChannelProvider;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpChannelConnector {

  private final HttpClientImpl client;
  private final ChannelGroup channelGroup;
  private final HttpClientOptions options;
  private final ClientMetrics metrics;
  private final SSLHelper sslHelper;
  private final HttpVersion version;
  private final SocketAddress peerAddress;
  private final SocketAddress server;

  public HttpChannelConnector(HttpClientImpl client,
                              ChannelGroup channelGroup,
                              ClientMetrics metrics,
                              HttpVersion version,
                              SSLHelper sslHelper,
                              SocketAddress peerAddress,
                              SocketAddress server) {
    this.client = client;
    this.channelGroup = channelGroup;
    this.metrics = metrics;
    this.options = client.getOptions();
    this.sslHelper = sslHelper;
    this.version = version;
    this.peerAddress = peerAddress;
    this.server = server;
  }

  public void connect(EventLoopContext context, Promise<HttpClientConnection> future) {

    boolean domainSocket = server.path() != null;

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.nettyEventLoop());

    applyConnectionOptions(domainSocket, bootstrap);

    ProxyOptions options = this.options.getProxyOptions();
    if (options != null && sslHelper == null && options.getType()== ProxyType.HTTP) {
      // http proxy requests are handled in HttpClientImpl, everything else can use netty proxy handler
      options = null;
    }
    ChannelProvider channelProvider = new ChannelProvider(bootstrap, sslHelper, context, options);
    // SocketAddress.inetSocketAddress(server.port(), peerHost)
    Future<Channel> fut = channelProvider.connect(server, peerAddress, this.options.isForceSni() ? peerAddress.host() : null, sslHelper != null);

    fut.addListener((GenericFutureListener<Future<Channel>>) res -> {
      if (res.isSuccess()) {
        Channel ch = res.getNow();
        channelGroup.add(ch);
        if (sslHelper != null) {
          String protocol = channelProvider.applicationProtocol();
          if (sslHelper.isUseAlpn()) {
            if ("h2".equals(protocol)) {
              applyHttp2ConnectionOptions(ch.pipeline());
              http2Connected(context, ch, future);
            } else {
              applyHttp1xConnectionOptions(ch.pipeline());
              HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
                HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
              http1xConnected(fallbackProtocol, server, true, context, ch, future);
            }
          } else {
            applyHttp1xConnectionOptions(ch.pipeline());
            http1xConnected(version, server, true, context, ch, future);
          }
        } else {
          ChannelPipeline pipeline = ch.pipeline();
          if (version == HttpVersion.HTTP_2) {
            if (this.options.isHttp2ClearTextUpgrade()) {
              applyHttp1xConnectionOptions(pipeline);
              http1xConnected(version, server, false, context, ch, future);
            } else {
              applyHttp2ConnectionOptions(pipeline);
              http2Connected(context, ch, future);
            }
          } else {
            applyHttp1xConnectionOptions(pipeline);
            http1xConnected(version, server, false, context, ch, future);
          }
        }
      } else {
        connectFailed(channelProvider.channel(), res.cause(), future);
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

  private void http1xConnected(HttpVersion version,
                               SocketAddress server,
                               boolean ssl,
                               ContextInternal context,
                               Channel ch,
                               Promise<HttpClientConnection> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && options.isHttp2ClearTextUpgrade();
    VertxHandler<Http1xClientConnection> clientHandler = VertxHandler.create(chctx -> {
      HttpClientMetrics met = client.metrics();
      Http1xClientConnection conn = new Http1xClientConnection(upgrade ? HttpVersion.HTTP_1_1 : version, client, chctx, ssl, server, context, this.metrics);
      if (met != null) {
        Object socketMetric = met.connected(conn.remoteAddress(), conn.remoteName());
        conn.metric(socketMetric);
        met.endpointConnected(metrics);
      }
      return conn;
    });
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        future.complete(new Http2UpgradedClientConnection(client, conn));
      } else {
        future.complete(conn);
      }
    });
    clientHandler.removeHandler(conn -> {
      conn.lifecycleHandler().handle(false);
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  private void http2Connected(EventLoopContext context,
                              Channel ch,
                              Promise<HttpClientConnection> future) {
    try {
      VertxHttp2ConnectionHandler<Http2ClientConnection> clientHandler = Http2ClientConnection.createHttp2ConnectionHandler(client, metrics, context, null, conn -> {
        future.complete(conn);
      });
      ch.pipeline().addLast("handler", clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, e, future);
    }
  }

  private void connectFailed(Channel ch, Throwable t, Promise<HttpClientConnection> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }
}
