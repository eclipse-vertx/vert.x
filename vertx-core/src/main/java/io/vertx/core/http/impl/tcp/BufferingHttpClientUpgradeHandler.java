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
package io.vertx.core.http.impl.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.Deque;

import static io.vertx.core.http.impl.tcp.Http2UpgradeClientConnection.SEND_BUFFERED_MESSAGES_EVENT;

/**
 * Buffers the messages received from the server until the upgrade request is fully sent, signaled by
 * {@link Http2UpgradeClientConnection#SEND_BUFFERED_MESSAGES_EVENT}.
 *
 * <p>The client must only process the response once the request is fully sent: processing the 101 response
 * installs the HTTP/2 handlers, which send the client preface and remove the HTTP/1 encoder, before the rest of the
 * upgrade request is sent.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BufferingHttpClientUpgradeHandler extends HttpClientUpgradeHandler {

  private long bufferedSize = 0;
  private Deque<Object> buffered = new ArrayDeque<>();

  public BufferingHttpClientUpgradeHandler(SourceCodec sourceCodec, UpgradeCodec upgradeCodec, int maxContentLength) {
    super(sourceCodec, upgradeCodec, maxContentLength);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (buffered != null) {
      int maxContent = maxContentLength();
      boolean lower = bufferedSize < maxContent;
      if (msg instanceof ByteBufHolder) {
        bufferedSize += ((ByteBufHolder)msg).content().readableBytes();
      } else if (msg instanceof ByteBuf) {
        bufferedSize += ((ByteBuf)msg).readableBytes();
      }
      buffered.add(msg);

      if (bufferedSize >= maxContent && lower) {
        ctx.fireExceptionCaught(new TooLongFrameException("Max content exceeded " + maxContentLength() + " bytes."));
      }
    } else {
      super.channelRead(ctx, msg);
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (SEND_BUFFERED_MESSAGES_EVENT == evt) {
      Deque<Object> messages = buffered;
      buffered = null;
      Object msg;
      while ((msg = messages.poll()) != null) {
        super.channelRead(ctx, msg);
      }
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    if (buffered != null) {
      Deque<Object> messages = buffered;
      buffered = null;
      Object msg;
      while ((msg = messages.poll()) != null) {
        ReferenceCountUtil.release(msg);
      }
    }
    super.handlerRemoved(ctx);
  }
}
