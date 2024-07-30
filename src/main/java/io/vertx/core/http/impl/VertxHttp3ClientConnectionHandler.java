/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3ConnectionHandler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttp3ClientConnectionHandler extends ChannelHandlerAdapter implements ChannelInboundHandler {
  private Http3ConnectionHandler http3ConnectionHandler = new Http3ClientConnectionHandler();

  public VertxHttp3ClientConnectionHandler() {
    super();
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    http3ConnectionHandler.channelRegistered(ctx);
  }

  @Override
  public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
    http3ConnectionHandler.channelUnregistered(ctx);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    http3ConnectionHandler.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    http3ConnectionHandler.channelInactive(ctx);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    http3ConnectionHandler.channelRead(ctx, msg);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    http3ConnectionHandler.channelReadComplete(ctx);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    http3ConnectionHandler.userEventTriggered(ctx, evt);
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    http3ConnectionHandler.channelWritabilityChanged(ctx);
  }
}
