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
package org.vertx.java.core.datagram.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.VertxHandler;

import java.util.HashMap;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramServerHandler extends VertxHandler<DefaultDatagramSocket> {
  private final DefaultDatagramSocket server;

  DatagramServerHandler(VertxInternal vertx, DefaultDatagramSocket server) {
        super(vertx, new HashMap<Channel, DefaultDatagramSocket>());
    this.server = server;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    connectionMap.put(ctx.channel(), server);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void channelRead(final DefaultDatagramSocket server, final DefaultContext context, ChannelHandlerContext chctx, final Object msg) throws Exception {
    if (context.isOnCorrectWorker(chctx.channel().eventLoop())) {
      try {
        vertx.setContext(context);
        server.handleMessage((org.vertx.java.core.datagram.DatagramPacket) msg);
      } catch (Throwable t) {
        context.reportException(t);
      }
    } else {
      context.execute(new Runnable() {
        public void run() {
          try {
            server.handleMessage((org.vertx.java.core.datagram.DatagramPacket) msg);
          } catch (Throwable t) {
            context.reportException(t);
          }
        }
      });
    }
  }

  @Override
  protected Object safeObject(Object msg) throws Exception {
    if (msg instanceof DatagramPacket) {
      DatagramPacket packet = (DatagramPacket) msg;
      ByteBuf content = packet.content();
      if (content.isDirect())  {
        content = safeBuffer(content);
      }
      return new DefaultDatagramPacket(packet.sender(), new Buffer(content));
    }
    return msg;
  }
}
