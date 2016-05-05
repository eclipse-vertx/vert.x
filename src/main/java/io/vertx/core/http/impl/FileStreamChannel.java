/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoop;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.io.RandomAccessFile;
import java.net.SocketAddress;

/**
 * A channel used for writing a file in an HTTP2 stream.
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
      Handler<AsyncResult<Long>> resultHandler,
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
              ChannelFuture fut = ctx.writeAndFlush(new ChunkedFile((RandomAccessFile) evt, offset, length, 8192 /* default chunk size */));
              fut.addListener(f -> {
                if (resultHandler != null) {
                  if (f.isSuccess()) {
                    resultHandler.handle(Future.succeededFuture(bytesWritten));
                  } else {
                    resultHandler.handle(Future.failedFuture(f.cause()));
                  }
                }
                fut.addListener(ChannelFutureListener.CLOSE);
              });
            }
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
    return loop instanceof NioEventLoop;
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

    private Id() {
    }

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
