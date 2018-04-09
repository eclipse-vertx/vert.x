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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler that detects whether the HTTP/2 connection preface or just process the request
 * with the HTTP 1.x pipeline to support H2C with prior knowledge, i.e a client that connects
 * and uses HTTP/2 in clear text directly without an HTTP upgrade.
 */
public class Http1xOrH2CHandler extends ChannelInboundHandlerAdapter {

  public static final String HTTP_2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";
  private static final byte[] HTTP_2_PREFACE_ARRAY = HTTP_2_PREFACE.getBytes();
  private int current = 0;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf buf = (ByteBuf) msg;
    int len = Math.min(buf.readableBytes(), HTTP_2_PREFACE_ARRAY.length - current);
    int i = 0;
    while (i < len) {
      if (buf.getByte(buf.readerIndex() + i) != HTTP_2_PREFACE_ARRAY[current + i]) {
        end(ctx, buf, false);
        return;
      }
      i++;
    }
    if (current + i == HTTP_2_PREFACE_ARRAY.length) {
      end(ctx, buf, true);
    } else {
      current += len;
      buf.release();
    }
  }

  private void end(ChannelHandlerContext ctx, ByteBuf buf, boolean h2c) {
    if (current > 0) {
      ByteBuf msg = Unpooled.buffer(current + buf.readableBytes());
      msg.writeBytes(HTTP_2_PREFACE_ARRAY, 0, current);
      msg.writeBytes(buf);
      buf.release();
      buf = msg;
    }
    configure(ctx, h2c);
    ctx.pipeline().remove(this);
    ctx.fireChannelRead(buf);
  }

  protected void configure(ChannelHandlerContext ctx, boolean h2c) {
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    Channel channel = ctx.channel();
    channel.close();
  }
}
