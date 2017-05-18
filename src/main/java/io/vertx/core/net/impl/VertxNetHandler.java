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

import java.util.Map;
import java.util.function.Function;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxNetHandler<C extends ConnectionBase> extends VertxHandler<C> {

  private final Function<ChannelHandlerContext, C> connectionFactory;
  private final Channel ch;
  private ChannelHandlerContext chctx;

  public VertxNetHandler(Channel ch, Function<ChannelHandlerContext, C> connectionFactory) {
    this.ch = ch;
    this.connectionFactory = connectionFactory;
  }

  public VertxNetHandler(Channel ch, C conn) {
    this(ch, ctx -> conn);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    chctx = ctx;
    setConnection(connectionFactory.apply(ctx));
  }

  ChannelHandlerContext context() {
    return chctx;
  }

  @Override
  protected Object decode(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof ByteBuf) {
      return safeBuffer((ByteBuf) msg, allocator);
    }
    return msg;
  }
}
