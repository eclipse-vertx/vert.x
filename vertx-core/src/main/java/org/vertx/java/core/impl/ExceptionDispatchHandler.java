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
package org.vertx.java.core.impl;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOperationHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;

import java.net.SocketAddress;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@ChannelHandler.Sharable
public class ExceptionDispatchHandler extends ChannelHandlerAdapter implements ChannelOperationHandler {
  @Override
  public void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
    ctx.sendFile(region, promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
    ctx.bind(localAddress, promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
    ctx.connect(remoteAddress, localAddress, promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    ctx.disconnect(promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    ctx.close(promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    ctx.deregister(promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    ctx.flush(promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
  }

  @Override
  public void read(ChannelHandlerContext ctx) {
    ctx.read();
  }
}
