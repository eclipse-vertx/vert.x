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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelOption;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.quic.ConnectionClose;
import io.vertx.core.quic.QuicConnection;
import io.vertx.core.quic.QuicStream;

import javax.net.ssl.SSLEngine;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicConnectionImpl extends ConnectionBase implements QuicConnectionInternal {

  private final ContextInternal context;
  private final QuicChannel channel;
  private final ChannelGroup channelGroup;
  private Handler<QuicStream> handler;
  private ConnectionClose closePayload;

  public QuicConnectionImpl(ContextInternal context, QuicChannel channel, ChannelHandlerContext chctx) {
    super(context, chctx);
    this.channel = channel;
    this.context = context;
    this.channelGroup = new DefaultChannelGroup(context.nettyEventLoop(), true);
  }

  void handleStream(QuicStreamChannel streamChannel) {
    channelGroup.add(streamChannel);
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, context, streamChannel, chctx));
    handler.addHandler(stream -> {
      Handler<QuicStream> h = QuicConnectionImpl.this.handler;
      if (h != null) {
        context.dispatch(stream, h);
      }
    });
    streamChannel.pipeline().addLast(handler);
  }

  @Override
  public QuicConnectionImpl closeHandler(Handler<Void> handler) {
    return (QuicConnectionImpl) super.closeHandler(handler);
  }

  @Override
  protected void handleException(Throwable t) {
    super.handleException(t);
  }

  void handleClosed(ConnectionClose payload) {
    this.closePayload = payload;
    handleClosed();
  }

  @Override
  public Future<Void> close(ConnectionClose payload) {
    PromiseInternal<Void> p = context.promise();
    close(true, payload.getError(), ((BufferInternal) payload.getReason()).getByteBuf(), p);
    return p.future();
  }

  private void close(boolean applicationClose, int error, ByteBuf reason, PromiseInternal<Void> promise) {
    EventExecutor exec = chctx.executor();
    if (exec.inEventLoop()) {
      ChannelFuture future = channel.close(applicationClose, error, reason);
      future.addListener(promise);
    } else {
      exec.execute(() -> close(applicationClose, error, reason, promise));
    }
  }

  @Override
  public QuicConnection handler(Handler<QuicStream> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public Future<QuicStream> createStream() {
    // TODO : should use get or create context and test it ....
    Promise<QuicStream> promise = context.promise();
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, context, (QuicStreamChannel) chctx.channel(), chctx));
    handler.addHandler(stream -> {
      stream.channel().config().setOption(QuicChannelOption.READ_FRAMES, true);
      promise.tryComplete(stream);
    });
    io.netty.util.concurrent.Future<QuicStreamChannel> future = channel.createStream(QuicStreamType.BIDIRECTIONAL, handler);
    future.addListener(future1 -> {
      if (!future1.isSuccess()) {
        promise.tryFail(future1.cause());
      }
    });
    return promise.future();
  }

  @Override
  public String applicationLayerProtocol() {
    SSLEngine engine = channel.sslEngine();
    return engine.getApplicationProtocol();
  }

  @Override
  public ConnectionClose closePayload() {
    return closePayload;
  }
}
