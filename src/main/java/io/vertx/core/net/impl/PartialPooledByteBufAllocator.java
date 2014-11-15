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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

import java.net.SocketAddress;

/**
 * A {@link io.netty.buffer.ByteBufAllocator} which is partial pooled. Which means only direct {@link io.netty.buffer.ByteBuf}s are pooled. The rest
 * is unpooled.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class PartialPooledByteBufAllocator implements ByteBufAllocator {
  // Make sure we use the same number of areas as EventLoop's to reduce condition.
  // We can remove this once the following netty issue is fixed:
  // See https://github.com/netty/netty/issues/2264
  private static final ByteBufAllocator POOLED = new PooledByteBufAllocator(false);
  private static final ByteBufAllocator UNPOOLED = new UnpooledByteBufAllocator(false);

  public static final PartialPooledByteBufAllocator INSTANCE = new PartialPooledByteBufAllocator();

  private PartialPooledByteBufAllocator() { }

  @Override
  public ByteBuf buffer() {
    return UNPOOLED.heapBuffer();
  }

  @Override
  public ByteBuf buffer(int initialCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity);
  }

  @Override
  public ByteBuf buffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public ByteBuf ioBuffer() {
    return POOLED.directBuffer();
  }

  @Override
  public ByteBuf ioBuffer(int initialCapacity) {
    return POOLED.directBuffer(initialCapacity);
  }

  @Override
  public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
    return POOLED.directBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public ByteBuf heapBuffer() {
    return UNPOOLED.heapBuffer();
  }

  @Override
  public ByteBuf heapBuffer(int initialCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity);
  }

  @Override
  public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public ByteBuf directBuffer() {
    return POOLED.directBuffer();
  }

  @Override
  public ByteBuf directBuffer(int initialCapacity) {
    return POOLED.directBuffer(initialCapacity);
  }

  @Override
  public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
    return POOLED.directBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public CompositeByteBuf compositeBuffer() {
    return UNPOOLED.compositeHeapBuffer();
  }

  @Override
  public CompositeByteBuf compositeBuffer(int maxNumComponents) {
    return UNPOOLED.compositeHeapBuffer(maxNumComponents);
  }

  @Override
  public CompositeByteBuf compositeHeapBuffer() {
    return UNPOOLED.compositeHeapBuffer();
  }

  @Override
  public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
    return UNPOOLED.compositeHeapBuffer(maxNumComponents);
  }

  @Override
  public CompositeByteBuf compositeDirectBuffer() {
    return POOLED.compositeDirectBuffer();
  }

  @Override
  public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
    return POOLED.compositeDirectBuffer();
  }

  @Override
  public boolean isDirectBufferPooled() {
    return true;
  }

  /**
   * Create a new {@link io.netty.channel.ChannelHandlerContext} which wraps the given one anf force the usage of direct buffers.
   */
  public static ChannelHandlerContext forceDirectAllocator(ChannelHandlerContext ctx) {
    return new PooledChannelHandlerContext(ctx);
  }

  private static final class PooledChannelHandlerContext implements ChannelHandlerContext {
    private final ChannelHandlerContext ctx;
    PooledChannelHandlerContext(ChannelHandlerContext ctx) {
      this.ctx = ctx;
    }
    @Override
    public Channel channel() {
      return ctx.channel();
    }

    @Override
    public EventExecutor executor() {
      return ctx.executor();
    }

    @Override
    public String name() {
      return ctx.name();
    }

    @Override
    public ChannelHandler handler() {
      return ctx.handler();
    }

    @Override
    public boolean isRemoved() {
      return ctx.isRemoved();
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
      ctx.fireChannelRegistered();
      return this;
    }

    @Deprecated
    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
      ctx.fireChannelUnregistered();
      return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
      ctx.fireChannelActive();
      return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
      ctx.fireChannelInactive();
      return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
      ctx.fireExceptionCaught(cause);
      return this;
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
      ctx.fireUserEventTriggered(event);
      return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
      ctx.fireChannelRead(msg);
      return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
      ctx.fireChannelReadComplete();
      return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
      ctx.fireChannelWritabilityChanged();
      return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
      return ctx.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
      return ctx.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
      return ctx.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
      return ctx.disconnect();
    }

    @Override
    public ChannelFuture close() {
      return ctx.close();
    }

    @Deprecated
    @Override
    public ChannelFuture deregister() {
      return ctx.deregister();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
      return ctx.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
      return ctx.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
      return ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
      return ctx.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
      return ctx.close(promise);
    }

    @Deprecated
    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
      return ctx.deregister(promise);
    }

    @Override
    public ChannelHandlerContext read() {
      ctx.read();
      return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
      return ctx.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
      return ctx.write(msg, promise);
    }

    @Override
    public ChannelHandlerContext flush() {
      ctx.flush();
      return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
      return ctx.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
      return ctx.writeAndFlush(msg);
    }

    @Override
    public ChannelPipeline pipeline() {
      return ctx.pipeline();
    }

    @Override
    public ByteBufAllocator alloc() {
      return ForceDirectPoooledByteBufAllocator.INSTANCE;
    }

    @Override
    public ChannelPromise newPromise() {
      return ctx.newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
      return ctx.newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
      return ctx.newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
      return ctx.newFailedFuture(cause);
    }

    @Override
    public ChannelPromise voidPromise() {
      return ctx.voidPromise();
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
      return ctx.attr(key);
    }
  }

  private static final class ForceDirectPoooledByteBufAllocator implements ByteBufAllocator {
    static ByteBufAllocator INSTANCE = new ForceDirectPoooledByteBufAllocator();

    @Override
    public ByteBuf buffer() {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
      return PartialPooledByteBufAllocator.INSTANCE.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
      return PartialPooledByteBufAllocator.INSTANCE.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
      return PartialPooledByteBufAllocator.INSTANCE.compositeBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
      return PartialPooledByteBufAllocator.INSTANCE.compositeBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
      return PartialPooledByteBufAllocator.INSTANCE.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
      return PartialPooledByteBufAllocator.INSTANCE.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
      return PartialPooledByteBufAllocator.INSTANCE.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
      return PartialPooledByteBufAllocator.INSTANCE.compositeDirectBuffer(maxNumComponents);
    }

    @Override
    public boolean isDirectBufferPooled() {
      return PartialPooledByteBufAllocator.INSTANCE.isDirectBufferPooled();
    }
  }
}
