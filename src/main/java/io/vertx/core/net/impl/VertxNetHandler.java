/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;

import java.util.function.Function;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxNetHandler extends VertxHandler<NetSocketImpl> {

  private final Function<ChannelHandlerContext, NetSocketImpl> connectionFactory;

  public VertxNetHandler(Function<ChannelHandlerContext, NetSocketImpl> connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public VertxNetHandler(NetSocketImpl conn) {
    this(ctx -> conn);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    setConnection(connectionFactory.apply(ctx));
  }
}
