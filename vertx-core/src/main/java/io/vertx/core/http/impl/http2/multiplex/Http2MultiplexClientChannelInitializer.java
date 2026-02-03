/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2.multiplex;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.http1.Http1ClientConnection;
import io.vertx.core.http.impl.tcp.Http2UpgradeClientConnection;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.http2.Http2ClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

public class Http2MultiplexClientChannelInitializer implements Http2ClientChannelInitializer {

  private Http2Settings initialSettings;
  private long keepAliveTimeoutMillis; // TimeUnit.SECONDS.toMillis(client.options.getHttp2KeepAliveTimeout())
  private int multiplexingLimit; // client.options.getHttp2MultiplexingLimit()
  private final boolean decompressionSupported;
  private final boolean logEnabled;

  public Http2MultiplexClientChannelInitializer(Http2Settings initialSettings,
                                                long keepAliveTimeoutMillis,
                                                int multiplexingLimit,
                                                boolean decompressionSupported,
                                                boolean logEnabled) {
    this.initialSettings = initialSettings;
    this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
    this.multiplexingLimit = multiplexingLimit;
    this.decompressionSupported = decompressionSupported;
    this.logEnabled = logEnabled;
  }

  @Override
  public void http2Connected(ContextInternal context, HostAndPort authority, TransportMetrics<?> transportMetrics, Object connectionMetric, Channel channel, ClientMetrics<?, ?, ?> clientMetrics, PromiseInternal<HttpClientConnection> promise) {
    Http2MultiplexConnectionFactory connectionFactory = connectionFactory(context, authority, transportMetrics, connectionMetric, clientMetrics, promise);
    io.vertx.core.http.impl.http2.multiplex.Http2MultiplexHandler handler = new io.vertx.core.http.impl.http2.multiplex.Http2MultiplexHandler(channel, context, connectionFactory, initialSettings);
    Http2FrameCodec http2FrameCodec = new Http2CustomFrameCodecBuilder(null, decompressionSupported).server(false)
      .initialSettings(initialSettings)
      .logEnabled(logEnabled)
      .build();
    channel.pipeline().addLast(http2FrameCodec);
    channel.pipeline().addLast(new io.netty.handler.codec.http2.Http2MultiplexHandler(handler));
    channel.pipeline().addLast(handler);
    http2FrameCodec.connection().addListener(handler);
  }

  @Override
  public Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade(Http1ClientConnection conn, ClientMetrics<?, ?, ?> clientMetrics) {
    return new MultiplexChannelUpgrade(conn.metrics(), conn.metric());
  }

  Http2MultiplexConnectionFactory connectionFactory(ContextInternal context, HostAndPort authority,
                                                    TransportMetrics<?> transportMetrics,
                                                    Object connectionMetric,
                                                    ClientMetrics<?, ?, ?> clientMetrics, Promise<HttpClientConnection> promise) {
    return (handler, chctx) -> {
      Http2MultiplexClientConnection connection = new Http2MultiplexClientConnection(handler, chctx, context, clientMetrics, transportMetrics, authority, multiplexingLimit,
        keepAliveTimeoutMillis,
        decompressionSupported,
        promise);
      connection.metric(connectionMetric);
      return connection;
    };
  }

  public class MultiplexChannelUpgrade implements Http2UpgradeClientConnection.Http2ChannelUpgrade {

    private final TransportMetrics<?> transportMetrics;
    private final Object connectionMetric;

    public MultiplexChannelUpgrade(TransportMetrics<?> transportMetrics, Object connectionMetric) {
      this.transportMetrics = transportMetrics;
      this.connectionMetric = connectionMetric;
    }

    @Override
    public void upgrade(HttpClientStream upgradingStream,
                        HttpRequestHead request,
                        Buffer content,
                        boolean end,
                        Channel channel,
                        ClientMetrics<?, ?, ?> clientMetrics, Http2UpgradeClientConnection.UpgradeResult result) {
      ChannelPipeline pipeline = channel.pipeline();
      HttpClientCodec clientCodec = pipeline.get(HttpClientCodec.class);
      Http2ConnectionHandler http2FrameCodec = new Http2CustomFrameCodecBuilder(null, decompressionSupported)
        .server(false)
        .initialSettings(initialSettings)
        .logEnabled(logEnabled)
        .build();
      ContextInternal context = upgradingStream.context();
      PromiseInternal<HttpClientConnection> p = context.promise();
      Http2MultiplexConnectionFactory connectionFactory = connectionFactory(context, request.authority, transportMetrics, connectionMetric, clientMetrics, p);
      io.vertx.core.http.impl.http2.multiplex.Http2MultiplexHandler handler = new io.vertx.core.http.impl.http2.multiplex.Http2MultiplexHandler(
        channel,
        context,
        connectionFactory,
        initialSettings);
      http2FrameCodec.connection().addListener(handler);
      io.netty.handler.codec.http2.Http2MultiplexHandler multiplex = new io.netty.handler.codec.http2.Http2MultiplexHandler(handler, new ChannelDuplexHandler() {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
          ctx.channel().config().setAutoRead(false);
          pipeline.replace("handler", "handler", handler);
          Http2MultiplexClientConnection connection = (Http2MultiplexClientConnection) handler.connection();
          ctx.pipeline().addLast(handler);
          HttpClientStream upgradedStream = handler.upgradeClientStream((Http2StreamChannel) ctx.channel(), upgradingStream.metric(), upgradingStream.trace(), upgradingStream.context()).unwrap();
          ctx.pipeline().remove(this);
          p.onSuccess(ar -> {
            result.upgradeAccepted(connection, upgradedStream);
            ctx.channel().config().setAutoRead(true);
          });
          super.channelActive(ctx);
        }
      });
      Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(http2FrameCodec, multiplex);
      HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(clientCodec, upgradeCodec, 10_000);
      pipeline.addBefore("handler", "upgrade", upgradeHandler);
      pipeline.addBefore("handler", "reject-checker", new ChannelDuplexHandler() {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
            result.upgradeRejected();
            pipeline.remove(this);
          } else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
            pipeline.remove(this);
          }
          super.userEventTriggered(ctx, evt);
        }
      });
    }
  }
}
