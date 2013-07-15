/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Modified from original form by Tim Fox
 */
package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@ChannelHandler.Sharable
public class ByteBufHandler extends ChannelInboundHandlerAdapter {

  public static final ByteBufHandler INSTANCE = new ByteBufHandler();

  private ByteBufHandler() {}

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof ByteBuf) {
      ByteBufAllocator allocator = ctx.alloc();
      if (allocator instanceof UnpooledByteBufAllocator) {
        ByteBuf buffer = (ByteBuf) msg;
        if (buffer.isDirect()) {
          // if its a direct buffer an we use unpooled ByteBufAllocator copy the content to a heapbuffer and release
          // the direct one. This is needed to prevent OOM because of slow GC release of direct buffers.
          ByteBuf buf = allocator.buffer(buffer.readableBytes());
          buf.writeBytes(buffer);
          buffer.release();
          msg = buf;
        }
        // Wrap the buffer to make it unreleasable and so let the user store references to the Buffer.
        msg = Unpooled.unreleasableBuffer((ByteBuf) msg);
      }
    }

    ctx.fireChannelRead(msg);
  }
}
