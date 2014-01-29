/*
 * Copyright (c) 2011-2013 The original author or authors
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
package org.vertx.java.core.datagram.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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
  protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof DatagramPacket) {
      DatagramPacket packet = (DatagramPacket) msg;
      ByteBuf content = packet.content();
      if (content.isDirect())  {
        content = safeBuffer(content, allocator);
      }
      return new DefaultDatagramPacket(packet.sender(), new Buffer(content));
    }
    return msg;
  }
}
