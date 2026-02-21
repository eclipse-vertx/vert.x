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
package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3Frame;
import io.netty.handler.codec.http3.Http3GoAwayFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3Settings;
import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@ChannelHandler.Sharable
public class Http3FrameLogger extends ChannelDuplexHandler {

  private static final int BUFFER_LENGTH_THRESHOLD = 64;

  private final InternalLogLevel level;
  private final InternalLogger logger;

  public Http3FrameLogger(InternalLogLevel level) {
    this.level = level;
    this.logger = InternalLoggerFactory.getInstance(Http3FrameLogger.class);
  }

  public void log(boolean inbound, QuicStreamChannel stream, Http3Frame frame) {
    long type = frame.type();
    if (logger.isEnabled(level) && type >= 0 && type < 16) {
      ChannelId streamId = stream.id();
      String direction = inbound ? "INBOUND" : "OUTBOUND";
      switch ((int)frame.type()) {
        // Data
        case 0x0:
          Http3DataFrame dataFrame = (Http3DataFrame) frame;
          ByteBuf data = dataFrame.content();
          logger.log(level, "{} {} DATA: streamId={} length={} bytes={}", stream,
            direction, streamId, data.readableBytes(), toString(data));
          break;
        case 0x1:
          Http3HeadersFrame headersFrame = (Http3HeadersFrame) frame;
          logger.log(level, "{} {} HEADERS: streamId={} headers={}", stream,
            direction, streamId, headersFrame.headers());
          break;
        case 0x4:
          Http3SettingsFrame settingsFrame = (Http3SettingsFrame) frame;
          Http3Settings settings = settingsFrame.settings();
          logger.log(level, "{} {} SETTINGS: streamId={} settings={}", stream, direction, streamId, settings);
          break;
        case 0x7:
          Http3GoAwayFrame goAwayFrame = (Http3GoAwayFrame) frame;
          logger.log(level, "{} {} GO_AWAY: streamId={} id={}", stream,
            direction, streamId, goAwayFrame.id());
          break;
      }
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
    if (msg instanceof Http3Frame) {
      Http3Frame frame = (Http3Frame) msg;
      log(true, stream, frame);
    }
    super.channelRead(ctx, msg);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
    if (msg instanceof Http3Frame) {
      Http3Frame frame = (Http3Frame) msg;
      log(false, stream, frame);
    }
    super.write(ctx, msg, promise);
  }

  private String toString(ByteBuf buf) {
    if (level == InternalLogLevel.TRACE || buf.readableBytes() <= BUFFER_LENGTH_THRESHOLD) {
      // Log the entire buffer.
      return ByteBufUtil.hexDump(buf);
    }

    // Otherwise just log the first 64 bytes.
    int length = Math.min(buf.readableBytes(), BUFFER_LENGTH_THRESHOLD);
    return ByteBufUtil.hexDump(buf, buf.readerIndex(), length) + "...";
  }
}
