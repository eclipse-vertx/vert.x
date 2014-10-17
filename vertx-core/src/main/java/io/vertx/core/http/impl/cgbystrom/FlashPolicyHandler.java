/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.http.impl.cgbystrom;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

/**
 * A Flash policy file handler
 * Will detect connection attempts made by Adobe Flash clients and return a policy file response
 *
 * After the policy has been sent, it will instantly close the connection.
 * If the first bytes sent are not a policy file request the handler will simply remove itself
 * from the pipeline.
 *
 * Read more at http://www.adobe.com/devnet/articles/crossdomain_policy_file_spec.html
 *
 * Example usage:
 * <code>
 * ChannelPipeline pipeline = Channels.pipeline();
 * pipeline.addLast("flashPolicy", new FlashPolicyHandler());
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 * pipeline.addLast("handler", new MyBusinessLogicHandler());
 * </code>
 *
 * For license see LICENSE file in this directory
 */
public class FlashPolicyHandler extends ChannelInboundHandlerAdapter {
  private static final String XML = "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>";

  enum ParseState {
    MAGIC1,
    MAGIC2
  }

  private ParseState state = ParseState.MAGIC1;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf buffer = (ByteBuf) msg;
    int index = buffer.readerIndex();
    switch (state) {
      case MAGIC1:
        if (!buffer.isReadable()) {
          return;
        }
        final int magic1 = buffer.getUnsignedByte(index++);
        state = ParseState.MAGIC2;
        if (magic1 != '<') {
          ctx.fireChannelRead(buffer);
          ctx.pipeline().remove(this);
          return;
        }
        // fall through
      case MAGIC2:
        if (!buffer.isReadable()) {
          return;
        }
        final int magic2 = buffer.getUnsignedByte(index);
        if (magic2 != 'p') {
          ctx.fireChannelRead(buffer);
          ctx.pipeline().remove(this);
        } else {
          ctx.writeAndFlush(Unpooled.copiedBuffer(XML, CharsetUtil.UTF_8)).addListener(ChannelFutureListener.CLOSE);
        }
    }
  }
}