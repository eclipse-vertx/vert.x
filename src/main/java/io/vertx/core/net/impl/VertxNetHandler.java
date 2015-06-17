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

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxNetHandler extends VertxHandler<NetSocketImpl> {

  private final Map<Channel, NetSocketImpl> connectionMap;

  public VertxNetHandler(VertxInternal vertx, Map<Channel, NetSocketImpl> connectionMap) {
    super(vertx);
    this.connectionMap = connectionMap;
  }

  @Override
  protected NetSocketImpl getConnection(Channel channel) {
    return connectionMap.get(channel);
  }

  @Override
  protected NetSocketImpl removeConnection(Channel channel) {
    return connectionMap.remove(channel);
  }

  @Override
  protected void channelRead(NetSocketImpl sock, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {
    ByteBuf buf = (ByteBuf) msg;
    if (sock != null) {
      context.executeFromIO(() -> {
        Buffer buff = Buffer.buffer(buf);
        System.out.println("Ref count going in: " + buf.refCnt());
        try {
          sock.handleDataReceived(buff);
        } finally {
          System.out.println("Ref count in finally: " + buf.refCnt());
          buff.release();
        }
      });
    } else {
      // just discard ?
      buf.release();
    }
  }

}
