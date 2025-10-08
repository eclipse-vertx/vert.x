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
package io.vertx.core.net.impl.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ConnectionGroup;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.QuicConnectionClose;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicStream;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import javax.net.ssl.SSLEngine;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicConnectionImpl extends ConnectionBase implements QuicConnectionInternal {

  private final ContextInternal context;
  private final QuicChannel channel;
  private final TransportMetrics<?> metrics;
  private final ConnectionGroup streamGroup;
  private Handler<QuicStream> handler;
  private Handler<Buffer> datagramHandler;
  private QuicConnectionClose closePayload;
  private final NetworkMetrics<?> streamMetrics;

  public QuicConnectionImpl(ContextInternal context, TransportMetrics metrics, QuicChannel channel, ChannelHandlerContext chctx) {
    super(context, chctx);
    this.channel = channel;
    this.metrics = metrics;
    this.context = context;
    this.streamGroup = new ConnectionGroup(context.nettyEventLoop()) {
      @Override
      protected void handleClose(Completable<Void> completion) {
        BufferInternal reason = (BufferInternal) closePayload.getReason();
        ChannelFuture future = channel.close(false, closePayload.getError(), reason != null ? reason.getByteBuf() : Unpooled.EMPTY_BUFFER);
        PromiseInternal<Void> promise = (PromiseInternal<Void>) completion;
        future.addListener(promise);
      }
    };
    if (metrics != null) {
      this.streamMetrics = new NetworkMetrics<>() {
        @Override
        public void bytesRead(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
          metrics.bytesRead(metric(), remoteAddress, numberOfBytes);
        }
        @Override
        public void bytesWritten(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
          metrics.bytesWritten(metric(), remoteAddress, numberOfBytes);
        }
      };
    } else {
      this.streamMetrics = null;
    }
  }

  @Override
  public NetworkMetrics<?> metrics() {
    return metrics;
  }

  void handleStream(QuicStreamChannel streamChannel) {
    streamGroup.add(streamChannel);
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, context, streamChannel, streamMetrics, chctx));
    handler.addHandler(stream -> {
      Handler<QuicStream> h = QuicConnectionImpl.this.handler;
      if (h != null) {
        context.dispatch(stream, h);
      }
    });
    streamChannel.pipeline().addLast("handler", handler);
  }

  void handleDatagram(ByteBuf byteBuf) {
    Handler<Buffer> handler = datagramHandler;
    if (handler != null) {
      Buffer datagram = BufferInternal.safeBuffer(byteBuf);
      context.emit(datagram, handler);
    } else {
      byteBuf.release();
    }
  }

  @Override
  public QuicConnectionImpl closeHandler(Handler<Void> handler) {
    return (QuicConnectionImpl) super.closeHandler(handler);
  }

  @Override
  protected void handleException(Throwable t) {
    super.handleException(t);
  }

  void handleClosed(QuicConnectionClose payload) {
    this.closePayload = payload;
    handleClosed();
  }

  @Override
  public Future<Void> close(QuicConnectionClose payload) {
    return shutdown(Duration.ZERO, payload);
  }

  @Override
  public Future<Void> shutdown(Duration timeout, QuicConnectionClose payload) {
    PromiseInternal<Void> p = context.promise();
    EventExecutor exec = chctx.executor();
    if (exec.inEventLoop()) {
      shutdown(timeout, payload, p);
    } else {
      exec.execute(() -> shutdown(timeout, payload, p));
    }
    return p.future();
  }

  private void shutdown(Duration timeout, QuicConnectionClose payload, PromiseInternal<Void> promise) {
    if (closePayload != null) {
      throw new IllegalStateException();
    }
    closePayload = payload;
    Future<Void> f = streamGroup.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
    f.onComplete(promise);
  }

  @Override
  public QuicConnection streamHandler(Handler<QuicStream> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public Future<QuicStream> createStream(boolean bidirectional) {
    // TODO : should use get or create context and test it ....
    Promise<QuicStream> promise = context.promise();
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, context, (QuicStreamChannel) chctx.channel(), streamMetrics, chctx));
    handler.addHandler(stream -> {
      promise.tryComplete(stream);
    });
    QuicStreamType type = bidirectional ? QuicStreamType.BIDIRECTIONAL : QuicStreamType.UNIDIRECTIONAL;
    io.netty.util.concurrent.Future<QuicStreamChannel> future = channel.createStream(type, new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        ch.pipeline().addLast("handler", handler);
      }
    });
    future.addListener(future1 -> {
      if (!future1.isSuccess()) {
        future1.cause().printStackTrace(System.out);
        promise.tryFail(future1.cause());
      }
    });
    return promise.future();
  }

  @Override
  public QuicConnection datagramHandler(Handler<Buffer> handler) {
    datagramHandler = handler;
    return this;
  }

  @Override
  public Future<Void> writeDatagram(Buffer buffer) {
    ByteBuf byteBuf = ((BufferInternal) buffer).getByteBuf();
    PromiseInternal<Void> promise = context.promise();
    ChannelFuture future = channel.writeAndFlush(byteBuf);
    future.addListener(promise);
    return promise.future();
  }

  @Override
  public String applicationLayerProtocol() {
    SSLEngine engine = channel.sslEngine();
    return engine.getApplicationProtocol();
  }

  @Override
  public QuicConnectionClose closePayload() {
    return closePayload;
  }
}
