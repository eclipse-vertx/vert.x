/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.quic.DefaultQuicStreamFrame;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamFrame;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.net.impl.VertxConnection;
import io.vertx.core.quic.QuicConnection;
import io.vertx.core.quic.QuicStream;
import io.vertx.core.streams.impl.InboundBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicStreamImpl extends VertxConnection implements QuicStream {

  private final QuicConnection connection;
  private final ContextInternal context;
  private final QuicStreamChannel channel;
  private final InboundMessageQueue<Object> pending;
  private Handler<Buffer> handler;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;

  QuicStreamImpl(QuicConnection connection, ContextInternal context, QuicStreamChannel channel, ChannelHandlerContext chctx) {
    super(context, chctx, false);
    this.connection = connection;
    this.context = context;
    this.channel = channel;
    this.pending = new InboundMessageQueue<>(context.eventLoop(), context.executor()) {
      @Override
      protected void handleResume() {
        QuicStreamImpl.this.doResume();
      }
      @Override
      protected void handlePause() {
        QuicStreamImpl.this.doPause();
      }
      @Override
      protected void handleMessage(Object msg) {
        if (msg == InboundBuffer.END_SENTINEL) {
          Handler<Void> h = endHandler;
          if (h != null) {
            context.dispatch(h);
          }
        } else {
          Handler<Buffer> h = handler;
          if (h != null) {
            context.dispatch((Buffer) msg, h);
          }
        }
      }
    };
  }

  @Override
  protected void handleWriteQueueDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  @Override
  protected void handleMessage(Object msg) {
    if (msg instanceof QuicStreamFrame) {
      QuicStreamFrame frame = (QuicStreamFrame) msg;
      boolean fin = frame.hasFin();
      ByteBuf safe;
      try {
        ByteBuf byteBuf = frame.content();
        safe = VertxByteBufAllocator.DEFAULT.heapBuffer(byteBuf.readableBytes());
        safe.writeBytes(byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
      } finally {
        frame.release();
      }
      handleFrame(BufferInternal.buffer(safe), fin);
    }
  }

  void handleFrame(Buffer buffer, boolean end) {
    Handler<Buffer> h = handler;
    if (h != null) {
      context.dispatch(buffer, h);
    }
    if (end) {
      Handler<Void> eh = endHandler;
      if (eh != null) {
        context.dispatch(null, eh);
      }
    }
  }

  @Override
  public QuicStreamImpl exceptionHandler(Handler<Throwable> handler) {
    return (QuicStreamImpl) super.exceptionHandler(handler);
  }

  @Override
  public QuicStream handler(Handler<Buffer> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public QuicStream pause() {
    pending.pause();
    return this;
  }

  @Override
  public QuicStream resume() {
    pending.fetch(Long.MAX_VALUE);
    return this;
  }

  @Override
  public QuicStream fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public QuicStream endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public QuicStream setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public QuicStream drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    return this;
  }

  @Override
  public Future<Void> write(Buffer data) {
    return write(new DefaultQuicStreamFrame(((BufferInternal) data).getByteBuf(), false));
  }

  @Override
  public Future<Void> end() {
    return write(QuicStreamFrame.EMPTY_FIN);
  }

  private Future<Void> write(QuicStreamFrame frame) {
    PromiseInternal<Void> promise = context.promise();
    writeToChannel(frame, promise);
    return promise.future();
  }

  @Override
  public boolean writeQueueFull() {
    return super.writeQueueFull();
  }

  @Override
  public QuicConnection connection() {
    return connection;
  }
}
