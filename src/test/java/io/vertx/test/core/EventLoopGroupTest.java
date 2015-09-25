/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopGroupTest extends VertxTestBase {

  @Test
  public void testGetEventLoopGroup() {

    EventLoopGroup elp = vertx.nettyEventLoopGroup();
    assertNotNull(elp);

  }

  @Test
  public void testNettyServerUsesContextEventLoop() throws Exception {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    AtomicReference<Thread> contextThread = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    context.runOnContext(v -> {
      contextThread.set(Thread.currentThread());
      latch.countDown();
    });
    awaitLatch(latch);
    ServerBootstrap bs = new ServerBootstrap();
    bs.group(context.nettyEventLoop());
    bs.channel(NioServerSocketChannel.class);
    bs.option(ChannelOption.SO_BACKLOG, 100);
    bs.childHandler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) throws Exception {
        assertSame(contextThread.get(), Thread.currentThread());
        context.executeFromIO(() -> {
          assertSame(contextThread.get(), Thread.currentThread());
          assertSame(context, Vertx.currentContext());
          ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
              assertSame(contextThread.get(), Thread.currentThread());
              context.executeFromIO(() -> {
                assertSame(contextThread.get(), Thread.currentThread());
                assertSame(context, Vertx.currentContext());
              });
            }
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
              ByteBuf buf = (ByteBuf) msg;
              assertEquals("hello", buf.toString(StandardCharsets.UTF_8));
              assertSame(contextThread.get(), Thread.currentThread());
              context.executeFromIO(() -> {
                assertSame(contextThread.get(), Thread.currentThread());
                assertSame(context, Vertx.currentContext());
              });
            }
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
              assertSame(contextThread.get(), Thread.currentThread());
              context.executeFromIO(() -> {
                assertSame(contextThread.get(), Thread.currentThread());
                assertSame(context, Vertx.currentContext());
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
              });
            }
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
              assertSame(contextThread.get(), Thread.currentThread());
              context.executeFromIO(() -> {
                assertSame(contextThread.get(), Thread.currentThread());
                assertSame(context, Vertx.currentContext());
                testComplete();
              });
            }
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
              fail(cause.getMessage());
            }
          });
        });
      }
    });
    bs.bind("localhost", 1234).sync();
    vertx.createNetClient(new NetClientOptions()).connect(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      NetSocket so = ar.result();
      so.write(Buffer.buffer("hello"));
    });
    await();
  }
}
