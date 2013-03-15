/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxNetHandler extends VertxStateHandler<DefaultNetSocket> implements ChannelInboundByteHandler {

  public VertxNetHandler(VertxInternal vertx, Map<Channel, DefaultNetSocket> connectionMap) {
    super(vertx, connectionMap);
  }

  @Override
  public ByteBuf newInboundBuffer(ChannelHandlerContext channelHandlerContext) throws Exception {
    return channelHandlerContext.alloc().ioBuffer();
  }

  @Override
  public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
    // just call clear as we always consume the whole buffer
    ctx.inboundByteBuffer().clear();
  }

  @Override
  public void freeInboundBuffer(ChannelHandlerContext channelHandlerContext) throws Exception {
    channelHandlerContext.inboundByteBuffer().release();
  }

  @Override
  public void inboundBufferUpdated(ChannelHandlerContext chctx) {
    final ByteBuf in = chctx.inboundByteBuffer();
    final DefaultNetSocket sock = connectionMap.get(chctx.channel());
    if (sock != null) {

      Channel ch = chctx.channel();
      Context context = getContext(sock);
      // We need to do this since it's possible the server is being used from a worker context
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        vertx.setContext(context);
        sock.handleDataReceived(new Buffer(in.slice()));
      } else {
        final ByteBuf buf = in.readBytes(in.readableBytes());
        context.execute(new Runnable() {
          public void run() {
            sock.handleDataReceived(new Buffer(buf));
          }
        });
      }
    }
  }
}
