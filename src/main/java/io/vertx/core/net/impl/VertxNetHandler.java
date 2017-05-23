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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.impl.ContextImpl;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxNetHandler<C extends ConnectionBase> extends VertxHandler<C> {

  private final Channel ch;
  private final Map<Channel, C> connectionMap;
  C conn; // We should try to make this private

  public VertxNetHandler(Channel ch, Map<Channel, C> connectionMap) {
    this.ch = ch;
    this.connectionMap = connectionMap;
  }

  public VertxNetHandler(Channel ch, C conn, Map<Channel, C> connectionMap) {
    this.ch = ch;
    this.connectionMap = connectionMap;
    this.conn = conn;
  }

  @Override
  protected C getConnection() {
    return conn;
  }

  @Override
  protected C removeConnection() {
    connectionMap.remove(ch);
    C conn = this.conn;
    this.conn = null;
    return conn;
  }

  @Override
  protected void channelRead(C conn, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {
    if (conn != null) {
      context.executeFromIO(() -> handleMsgReceived(conn, msg));
    } else {
      // just discard
    }
  }

  protected abstract void handleMsgReceived(C conn, Object msg);

  @Override
  protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof ByteBuf) {
      return safeBuffer((ByteBuf) msg, allocator);
    }
    return msg;
  }
}
