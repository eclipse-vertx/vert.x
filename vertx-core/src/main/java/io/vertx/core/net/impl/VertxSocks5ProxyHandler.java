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

package io.vertx.core.net.impl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxSocks5ProxyHandler extends ChannelDuplexHandler {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(VertxSocks5ProxyHandler.class);
  private final Socks5ProxyHandler proxy;
  private final SocketAddress remoteAddress;

  public VertxSocks5ProxyHandler(SocketAddress proxyAddress, SocketAddress remoteAddress) {
    this.proxy = new Socks5ProxyHandler(proxyAddress);
    this.remoteAddress = remoteAddress;
  }

  @Override
  public final void connect(ChannelHandlerContext ctx, SocketAddress ignored, SocketAddress localAddress,
                            ChannelPromise promise) throws Exception {
    logger.trace("Connect method called.");
    proxy.connect(ctx, this.remoteAddress, localAddress, promise);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    logger.trace("handlerAdded method called.");
    proxy.handlerAdded(ctx);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    proxy.channelActive(ctx);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    proxy.write(ctx, msg, promise);
  }

  @Override
  public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
    proxy.bind(ctx, localAddress, promise);
  }

  @Override
  public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    proxy.disconnect(ctx, promise);
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    proxy.close(ctx, promise);
  }

  @Override
  public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    proxy.deregister(ctx, promise);
  }

  @Override
  public void read(ChannelHandlerContext ctx) throws Exception {
    proxy.read(ctx);
  }

  @Override
  public void flush(ChannelHandlerContext ctx) throws Exception {
    proxy.flush(ctx);
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    proxy.channelRegistered(ctx);
  }

  @Override
  public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
    proxy.channelUnregistered(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    proxy.channelInactive(ctx);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    proxy.channelRead(ctx, msg);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    proxy.channelReadComplete(ctx);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    proxy.userEventTriggered(ctx, evt);
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    proxy.channelWritabilityChanged(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    proxy.exceptionCaught(ctx, cause);
  }

  @Override
  public boolean isSharable() {
    return proxy.isSharable();
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    proxy.handlerRemoved(ctx);
  }
}
