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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.net.impl.VertxHandler;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramServerHandler extends VertxHandler<DatagramSocketImpl.Connection> {

  private final DatagramSocketImpl socket;

  DatagramServerHandler(DatagramSocketImpl socket) {
    this.socket = socket;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    setConnection(socket.createConnection(ctx));
  }

  @Override
  protected void handleMessage(final DatagramSocketImpl.Connection server, final ContextImpl context, ChannelHandlerContext chctx, final Object msg) throws Exception {
    server.handlePacket((io.vertx.core.datagram.DatagramPacket) msg);
  }

  @Override
  protected Object decode(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof DatagramPacket) {
      DatagramPacket packet = (DatagramPacket) msg;
      ByteBuf content = packet.content();
      if (content.isDirect())  {
        content = safeBuffer(content, allocator);
      }
      return new DatagramPacketImpl(packet.sender(), Buffer.buffer(content));
    }
    return msg;
  }
}
