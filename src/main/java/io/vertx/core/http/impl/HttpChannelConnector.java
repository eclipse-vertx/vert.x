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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpChannelConnector {

  private final HttpClientImpl client;
  private final NetClientImpl netClient;
  private final HttpClientOptions options;
  private final ProxyOptions proxyOptions;
  private final ClientMetrics metrics;
  private final boolean ssl;
  private final boolean useAlpn;
  private final HttpVersion version;
  private final SocketAddress peerAddress;
  private final SocketAddress server;

  public HttpChannelConnector(HttpClientImpl client,
                              NetClientImpl netClient,
                              ProxyOptions proxyOptions,
                              ClientMetrics metrics,
                              HttpVersion version,
                              boolean ssl,
                              boolean useAlpn,
                              SocketAddress peerAddress,
                              SocketAddress server) {
    this.client = client;
    this.netClient = netClient;
    this.metrics = metrics;
    this.options = client.getOptions();
    this.proxyOptions = proxyOptions;
    this.ssl = ssl;
    this.useAlpn = useAlpn;
    this.version = version;
    this.peerAddress = peerAddress;
    this.server = server;
  }

  public SocketAddress server() {
    return server;
  }

  private void connect(EventLoopContext context, Promise<NetSocket> promise) {
    netClient.connectInternal(proxyOptions, server, peerAddress, this.options.isForceSni() ? peerAddress.host() : null, ssl, useAlpn, promise, context, 0);
  }

  public Future<HttpClientConnection> wrap(EventLoopContext context, NetSocket so_) {
    NetSocketInternal so = (NetSocketInternal) so_;
    Promise<HttpClientConnection> promise = context.promise();

    // Remove all un-necessary handlers
    ChannelPipeline pipeline = so.channelHandlerContext().pipeline();
    List<ChannelHandler> removedHandlers = new ArrayList<>();
    for (Map.Entry<String, ChannelHandler> stringChannelHandlerEntry : pipeline) {
      ChannelHandler handler = stringChannelHandlerEntry.getValue();
      if (!(handler instanceof SslHandler)) {
        removedHandlers.add(handler);
      }
    }
    removedHandlers.forEach(pipeline::remove);

    //
    Channel ch = so.channelHandlerContext().channel();
    if (ssl) {
      String protocol = so.applicationLayerProtocol();
      if (useAlpn) {
        if ("h2".equals(protocol)) {
          applyHttp2ConnectionOptions(ch.pipeline());
          http2Connected(context, ch, promise);
        } else {
          applyHttp1xConnectionOptions(ch.pipeline());
          HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
            HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
          http1xConnected(fallbackProtocol, server, true, context, ch, promise);
        }
      } else {
        applyHttp1xConnectionOptions(ch.pipeline());
        http1xConnected(version, server, true, context, ch, promise);
      }
    } else {
      if (version == HttpVersion.HTTP_2) {
        if (this.options.isHttp2ClearTextUpgrade()) {
          applyHttp1xConnectionOptions(pipeline);
          http1xConnected(version, server, false, context, ch, promise);
        } else {
          applyHttp2ConnectionOptions(pipeline);
          http2Connected(context, ch, promise);
        }
      } else {
        applyHttp1xConnectionOptions(pipeline);
        http1xConnected(version, server, false, context, ch, promise);
      }
    }
    return promise.future();
  }

  public Future<HttpClientConnection> httpConnect(EventLoopContext context) {
    Promise<NetSocket> promise = context.promise();
    Future<HttpClientConnection> fut = promise.future().flatMap(so -> wrap(context, so));
    connect(context, promise);
    return fut;
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
