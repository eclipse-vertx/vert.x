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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2UnknownFrame;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http2.Http2ClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientStream;
import io.vertx.core.http.impl.http2.Http2ClientStreamImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.ShutdownEvent;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_ENABLE_PUSH;

@ChannelHandler.Sharable
public final class Http2MultiplexHandler extends ChannelDuplexHandler implements io.netty.handler.codec.http2.Http2Connection.Listener {

  private final Channel channel;
  private final Http2MultiplexConnectionFactory connectionFactory;
  private final Map<Http2StreamChannel, ChannelHandlerContext> pendingChannels; // For clients
  private final Deque<PromiseInternal<Void>> pendingSettingsAcks;
  private Http2MultiplexConnection connection;
  private ChannelHandlerContext chctx;
  private Http2Settings localSettings;
  private Http2Settings remoteSettings;
  private GoAway goAwayStatus;

  public Http2MultiplexHandler(Channel channel,
                               ContextInternal context,
                               Http2MultiplexConnectionFactory connectionFactory,
                               Http2Settings initialSettings) {

    // Initial settings ack
    ArrayDeque<PromiseInternal<Void>> pendingAcks = new ArrayDeque<>();
    pendingAcks.add(context.promise());

    this.channel = channel;
    this.localSettings = initialSettings;
    this.pendingSettingsAcks = pendingAcks;
    this.pendingChannels = new HashMap<>();
    this.connectionFactory = connectionFactory;
  }

  Http2MultiplexConnection connection() {
    return connection;
  }

  Http2Settings localSettings() {
    return localSettings;
  }

  Http2Settings remoteSettings() {
    return remoteSettings;
  }

  GoAway goAwayStatus() {
    return goAwayStatus;
  }

  int windowsSize() {
    Http2ConnectionHandler codec = chctx.pipeline().get(Http2FrameCodec.class);
    Http2Connection connection = codec.connection();
    Http2Stream stream = connection.connectionStream();
    return connection.local().flowController().windowSize(stream);
  }

  void incrementWindowsSize(int windowSizeIncrement) throws Http2Exception {
    Http2ConnectionHandler codec = chctx.pipeline().get(Http2FrameCodec.class);
    Http2Connection connection = codec.connection();
    Http2Stream stream = connection.connectionStream();
    codec.decoder().flowController().incrementWindowSize(stream, windowSizeIncrement);
  }

  void createClientStream(Http2ClientStream stream) throws Exception {
    Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap(channel);
    bootstrap.handler(this);
    Future<Http2StreamChannel> fut;
    try {
      fut = bootstrap.open();
    } catch (Exception e) {
      throw e;
    }
    Http2StreamChannel streamChannel = fut.sync().get();
    ChannelHandlerContext chctx = pendingChannels.remove(streamChannel);
    connection.registerChannel(stream, streamChannel.stream(), chctx);
  }

  HttpClientStream upgradeClientStream(Http2StreamChannel channel, Object metric, Object trace,
                                       ContextInternal context) {
    ChannelHandlerContext chctx = pendingChannels.remove(channel);
    ClientMetrics<?, ?, ?> clientMetrics = ((Http2MultiplexClientConnection) connection).clientMetrics();
    Http2FrameStream s = channel.stream();
    Http2ClientStream sb = new Http2ClientStream(s.id(), (Http2ClientConnection) connection, context, null, false, clientMetrics, channel.isWritable());
    connection.registerChannel(sb, s, chctx);
    Http2ClientStreamImpl ret = new Http2ClientStreamImpl((Http2ClientConnection) connection, context, null, false, clientMetrics);
    ret.stream = sb;
    sb.handler(ret);
    sb.upgrade(metric, trace);
    return ret;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    Channel channel = ctx.channel();
    if (channel == this.channel) {
      this.chctx = ctx;
      this.connection = connectionFactory.createConnection(this, chctx);
    } else if (channel instanceof Http2StreamChannel) {
      if (connection.isServer()) {
        ctx.pipeline().addBefore(ctx.name(), "chunkedWriter", new ChunkedWriteHandler());
      } else {
        pendingChannels.put((Http2StreamChannel) channel, ctx);
      }
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    if (ctx.channel() == channel) {
      connection.onClose();
    } else if (ctx.channel() instanceof Http2StreamChannel) {
      Http2StreamChannel streamChannel = (Http2StreamChannel) ctx.channel();
      connection.onStreamClose(streamChannel.stream().id());
    }
    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    if (ctx.channel() == channel) {
      connection.onException(cause);
    } else if (ctx.channel() instanceof Http2StreamChannel) {
      Http2StreamChannel streamChannel = (Http2StreamChannel) ctx.channel();
      connection.onException(streamChannel.stream().id(), cause);
    } else {
      super.exceptionCaught(ctx, cause);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof Http2HeadersFrame) {
      Http2HeadersFrame frame = (Http2HeadersFrame) msg;
      Http2Headers headers = frame.headers();
      connection.receiveHeaders(ctx, frame.stream(), headers, frame.isEndStream());
    } else if (msg instanceof Http2DataFrame) {
      Http2DataFrame frame = (Http2DataFrame) msg;
      connection.receiveData(ctx, frame.stream().id(), frame.content(), frame.isEndStream(), frame.initialFlowControlledBytes());
    } else if (msg instanceof Http2UnknownFrame) {
      Http2UnknownFrame frame = (Http2UnknownFrame) msg;
      connection.receiveUnknownFrame(frame.stream().id(), frame.frameType(), frame.flags().value(), frame.content());
    } else if (msg instanceof Http2SettingsFrame) {
      Http2SettingsFrame frame = (Http2SettingsFrame) msg;
      remoteSettings = frame.settings();
      connection.receiveSettings(ctx, frame.settings());
    } else if (msg instanceof Http2SettingsAckFrame) {
      PromiseInternal<Void> pendingSettingAck = pendingSettingsAcks.poll();
      pendingSettingAck.complete();
    } else if (msg instanceof Http2GoAwayFrame) {
      Http2GoAwayFrame frame = (Http2GoAwayFrame) msg;
      connection.receiveGoAway(frame.errorCode(), frame.lastStreamId(), BufferInternal.buffer(frame.content()));
    } else if (msg instanceof Http2PingFrame) {
      Http2PingFrame frame = (Http2PingFrame) msg;
      connection.receivePingAck(frame.content(), frame.ack());
    }
    super.channelRead(ctx, msg);
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    Channel channel = ctx.channel();
    if (channel instanceof Http2StreamChannel) {
      Http2StreamChannel stream = (Http2StreamChannel) channel;
      connection.onWritabilityChanged(stream.stream().id());
    }
    super.channelWritabilityChanged(ctx);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof Http2ResetFrame) {
      Http2ResetFrame frame = (Http2ResetFrame) evt;
      connection.receiveResetFrame(frame.stream().id(), frame.errorCode());
    } else if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
      // Work-around
      HttpServerUpgradeHandler.UpgradeEvent upgradeEvent = (HttpServerUpgradeHandler.UpgradeEvent) evt;
      String settingsHeader = upgradeEvent.upgradeRequest().headers().get("HTTP2-Settings");
      remoteSettings = HttpUtils.decodeSettings(settingsHeader);
      connection.receiveSettings(ctx, remoteSettings);
    } else if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
      ((Http2MultiplexClientConnection)connection).onInitialSettingsSent();
    } if (evt instanceof IdleStateEvent) {
      connection.onIdle();
    } else if (evt instanceof ShutdownEvent) {
      ShutdownEvent shutdownEvt = (ShutdownEvent) evt;
      connection.shutdown(shutdownEvt.timeout(), shutdownEvt.timeUnit());
    }
    super.userEventTriggered(ctx, evt);
  }

  void writeGoAway(long code, ByteBuf content, PromiseInternal<Void> listener) {
    Http2GoAwayFrame frame = new DefaultHttp2GoAwayFrame(code, content);
    frame.lastStreamId();
    ChannelFuture fut = chctx.writeAndFlush(frame);
    fut.addListener(listener);
  }

  void writeSettings(Http2Settings update, PromiseInternal<Void> listener) {
    for (Map.Entry<Character, Long> entry : localSettings.entrySet()) {
      Character key = entry.getKey();
      if (Objects.equals(update.get(key), entry.getValue())) {
        // We can safely remove as this is a private copy
        update.remove(key);
      }
    }
    // This server does not support push currently
    update.remove(SETTINGS_ENABLE_PUSH);
    Http2SettingsFrame frame = new DefaultHttp2SettingsFrame(update);
    ChannelFuture future = chctx.writeAndFlush(frame);
    future.addListener((ChannelFutureListener) res -> {
      if (res.isSuccess()) {
        pendingSettingsAcks.add(listener);
      } else {
        listener.operationComplete(res);
      }
    });
    localSettings = update; // Make a copy ?
  }

  @Override
  public void onStreamAdded(Http2Stream stream) {
  }

  @Override
  public void onStreamActive(Http2Stream stream) {
  }

  @Override
  public void onStreamHalfClosed(Http2Stream stream) {
  }

  @Override
  public void onStreamClosed(Http2Stream stream) {
    if (connection instanceof Http2MultiplexClientConnection) {
      Http2MultiplexClientConnection clientConnection = (Http2MultiplexClientConnection) connection;
      clientConnection.refresh();
    }
  }

  @Override
  public void onStreamRemoved(Http2Stream stream) {
  }

  @Override
  public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    checkGoAway(lastStreamId, errorCode, debugData);
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    checkGoAway(lastStreamId, errorCode, debugData);
  }

  private void checkGoAway(int lastStreamId, long errorCode, ByteBuf debugData) {
    if (goAwayStatus == null) {
      goAwayStatus = new GoAway().setLastStreamId(lastStreamId).setErrorCode(errorCode);
      connection.onGoAway(errorCode, lastStreamId, BufferInternal.buffer(debugData));
    }
  }
}
