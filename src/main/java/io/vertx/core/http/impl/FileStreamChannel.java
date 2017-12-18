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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.io.RandomAccessFile;
import java.net.SocketAddress;

/**
 * A channel used for writing a file in an HTTP2 stream.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class FileStreamChannel extends AbstractChannel {

  private static final SocketAddress LOCAL_ADDRESS = new StreamSocketAddress();
  private static final SocketAddress REMOTE_ADDRESS = new StreamSocketAddress();
  private static final ChannelMetadata METADATA = new ChannelMetadata(true);

  private final ChannelConfig config = new DefaultChannelConfig(this);
  private boolean active;
  private boolean closed;
  private long bytesWritten;
  private final VertxHttp2Stream stream;

  FileStreamChannel(
      Future<Long> result,
      VertxHttp2Stream stream,
      long offset,
      long length) {
    super(null, Id.INSTANCE);

    pipeline().addLast(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new ChannelInboundHandlerAdapter() {
          @Override
          public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof RandomAccessFile) {
              ChannelFuture fut = ctx.writeAndFlush(new ChunkedFile((RandomAccessFile) evt, offset, length, 8192 /* default chunk size */ ));
              fut.addListener(f -> {
                if (f.isSuccess()) {
                  result.tryComplete(bytesWritten);
                } else {
                  result.tryFail(f.cause());
                }
                fut.addListener(ChannelFutureListener.CLOSE);
              });
            }
          }
          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            result.tryFail(cause);
          }
        });
      }
    });

    this.stream = stream;
  }

  final Handler<Void> drainHandler = v -> {
    flush();
  };

  @Override
  protected void doRegister() throws Exception {
    active = true;
  }

  @Override
  protected AbstractUnsafe newUnsafe() {
    return new DefaultUnsafe();
  }

  @Override
  protected boolean isCompatible(EventLoop loop) {
    return true;
  }

  @Override
  protected SocketAddress localAddress0() {
    return LOCAL_ADDRESS;
  }

  @Override
  protected SocketAddress remoteAddress0() {
    return REMOTE_ADDRESS;
  }

  @Override
  protected void doBind(SocketAddress localAddress) throws Exception {
  }

  @Override
  protected void doDisconnect() throws Exception {
    doClose();
  }

  @Override
  protected void doClose() throws Exception {
    active = false;
    closed = true;
  }

  @Override
  protected void doBeginRead() throws Exception {
  }

  @Override
  protected void doWrite(ChannelOutboundBuffer in) throws Exception {
    ByteBuf chunk;
    while (!stream.isNotWritable() && (chunk = (ByteBuf) in.current()) != null) {
      bytesWritten += chunk.readableBytes();
      stream.writeData(chunk.retain(), false);
      stream.handlerContext.flush();
      in.remove();
    }
  }

  @Override
  public ChannelConfig config() {
    return config;
  }

  @Override
  public boolean isOpen() {
    return !closed;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public ChannelMetadata metadata() {
    return METADATA;
  }

  private static class StreamSocketAddress extends SocketAddress {
    @Override
    public String toString() {
      return "stream";
    }
  }

  private class DefaultUnsafe extends AbstractUnsafe {
    @Override
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
      safeSetSuccess(promise);
    }
  }

  static class Id implements ChannelId {

    static final ChannelId INSTANCE = new Id();

    private Id() { }

    @Override
    public String asShortText() {
      return toString();
    }

    @Override
    public String asLongText() {
      return toString();
    }

    @Override
    public int compareTo(ChannelId o) {
      if (o instanceof Id) {
        return 0;
      }

      return asLongText().compareTo(o.asLongText());
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Id;
    }

    @Override
    public String toString() {
      return "stream";
    }
  }
}
