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
package io.vertx.core.datagram.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.VertxHandler;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramServerHandler extends VertxHandler<DatagramSocketImpl> {

  private final DatagramSocketImpl socket;

  DatagramServerHandler(VertxInternal vertx, DatagramSocketImpl socket) {
        super(vertx);
    this.socket = socket;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
  }

  @Override
  protected DatagramSocketImpl getConnection(Channel channel) {
    return socket;
  }

  @Override
  protected DatagramSocketImpl removeConnection(Channel channel) {
    return socket;
  }

  @Override
  protected void channelRead(final DatagramSocketImpl server, final ContextImpl context, ChannelHandlerContext chctx, final Object msg) throws Exception {

    if (msg instanceof DatagramPacket) {
      DatagramPacket packet = (DatagramPacket) msg;
      ByteBuf content = packet.content();
      Buffer buff = Buffer.buffer(content);
      io.vertx.core.datagram.DatagramPacket pack = new DatagramPacketImpl(packet.sender(), buff);
      context.executeFromIO(() -> {
        try {
          server.handlePacket(pack);
        } finally {
          buff.release();
        }
      });
    }
  }
}
