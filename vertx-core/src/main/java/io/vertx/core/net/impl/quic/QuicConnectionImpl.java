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
import io.netty.channel.*;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.quic.QuicTransportParameters;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
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
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ConnectionGroup;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import javax.net.ssl.SSLEngine;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicConnectionImpl extends ConnectionBase implements QuicConnectionInternal {

  private final ContextInternal context;
  private final QuicChannel channel;
  private final TransportMetrics<?> metrics;
  private final long idleTimeout;
  private final long readIdleTimeout;
  private final long writeIdleTimeout;
  private final ByteBufFormat activityLogging;
  private final ConnectionGroup streamGroup;
  private Function<ContextInternal, ContextInternal> streamContextProvider;
  private Handler<QuicStream> handler;
  private Handler<Duration> shutdownHandler;
  private Handler<Buffer> datagramHandler;
  private Handler<Void> graceHandler;
  private QuicConnectionClose closePayload;
  private final NetworkMetrics<?> streamMetrics;
  private final SocketAddress remoteAddress;
  private QuicTransportParams transportParams;

  public QuicConnectionImpl(ContextInternal context, TransportMetrics metrics, long idleTimeout,
                            long readIdleTimeout,  long writeIdleTimeout, ByteBufFormat activityLogging, QuicChannel channel,
                            SocketAddress remoteAddress, ChannelHandlerContext chctx) {
    super(context, chctx);
    this.channel = channel;
    this.metrics = metrics;
    this.idleTimeout = idleTimeout;
    this.readIdleTimeout = readIdleTimeout;
    this.writeIdleTimeout = writeIdleTimeout;
    this.activityLogging = activityLogging;
    this.context = context;
    this.remoteAddress = remoteAddress;
    this.streamGroup = new ConnectionGroup(context.nettyEventLoop()) {
      @Override
      protected void handleShutdown(Duration timeout, Completable<Void> completion) {
        Handler<Duration> handler = shutdownHandler;
        if (handler != null) {
          context.dispatch(timeout, handler);
        }
        super.handleShutdown(timeout, completion);
      }
      @Override
      protected void handleClose(Completable<Void> completion) {
        BufferInternal reason = (BufferInternal) closePayload.getReason();
        ChannelFuture future = channel.close(false, closePayload.getError(), reason != null ? reason.getByteBuf() : Unpooled.EMPTY_BUFFER);
        PromiseInternal<Void> promise = (PromiseInternal<Void>) completion;
        future.addListener(promise);
      }
      @Override
      protected void handleGrace(Completable<Void> completion) {
        Handler<Void> handler = graceHandler;
        if (handler != null) {
          context.dispatch(handler);
        }
        super.handleGrace(completion);
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
  public TransportMetrics<?> metrics() {
    return metrics;
  }

  void handleStream(QuicStreamChannel streamChannel) {
    if (streamChannel.type() == QuicStreamType.BIDIRECTIONAL || streamChannel.isLocalCreated()) {
      // Only consider stream we can end for shutdown, e.g. this excludes remote opened HTTP/3 control stream
      streamGroup.add(streamChannel);
    }
    ChannelPipeline pipeline = streamChannel.pipeline();
    configureStreamChannelPipeline(pipeline);
    Function<ContextInternal, ContextInternal> provider = streamContextProvider;
    ContextInternal streamContext;
    if (provider != null) {
      streamContext = provider.apply(context);
    } else {
      streamContext = context;
    }
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, streamContext, streamChannel, streamMetrics, chctx));
    handler.addHandler(stream -> {
      Handler<QuicStream> h = QuicConnectionImpl.this.handler;
      if (h != null) {
        context.dispatch(stream, h);
      }
    });
    pipeline.addLast("handler", handler);
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
  protected boolean handleException(Throwable t) {
    return super.handleException(t);
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
  public QuicConnection handler(Handler<QuicStream> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public QuicConnection shutdownHandler(Handler<Duration> handler) {
    this.shutdownHandler = handler;
    return this;
  }

  @Override
  public Future<QuicStream> openStream(ContextInternal context) {
    return openStream(context, true);
  }

  @Override
  public Future<QuicStream> openStream(boolean bidirectional) {
    return openStream(vertx.getOrCreateContext(), bidirectional);
  }

  @Override
  public Future<QuicStream> openStream(ContextInternal context, boolean bidirectional) {
    Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>> initializerProvider =
      quicStreamChannelConsumer -> new ChannelInitializer<>() {
      @Override
      protected void initChannel(QuicStreamChannel ch) throws Exception {
        quicStreamChannelConsumer.accept(ch);
      }
    };
    return openStream(context, bidirectional, initializerProvider);
  }

  @Override
  public Future<QuicStream> openStream(ContextInternal context, boolean bidirectional, Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>> initializerProvider) {
    Promise<QuicStream> promise = context.promise();
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, context, (QuicStreamChannel) chctx.channel(), streamMetrics, chctx));
    handler.addHandler(stream -> {
      promise.tryComplete(stream);
    });
    QuicStreamType type = bidirectional ? QuicStreamType.BIDIRECTIONAL : QuicStreamType.UNIDIRECTIONAL;
    ChannelInitializer<QuicStreamChannel> initializer = initializerProvider.apply(ch -> {
      ChannelPipeline pipeline = ch.pipeline();
      configureStreamChannelPipeline(pipeline);
      pipeline.addLast("handler", handler);
      streamGroup.add(ch);
    });
    io.netty.util.concurrent.Future<QuicStreamChannel> future = channel.createStream(type, initializer);
    future.addListener(future1 -> {
      if (!future1.isSuccess()) {
        promise.tryFail(future1.cause());
      }
    });
    return promise.future();
  }

  private void configureStreamChannelPipeline(ChannelPipeline pipeline) {
    if (activityLogging != null) {
      pipeline.addLast("logging", new LoggingHandler(activityLogging));
    }
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, TimeUnit.MILLISECONDS));
    }
  }

  @Override
  public QuicConnectionInternal graceHandler(Handler<Void> handler) {
    graceHandler = handler;
    return this;
  }

  @Override
  public QuicConnectionInternal streamContextProvider(Function<ContextInternal, ContextInternal> provider) {
    streamContextProvider = provider;
    return this;
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
  public QuicTransportParams transportParams() {
    QuicTransportParams params = transportParams;
    if (params == null) {
      QuicTransportParameters delegate = channel.peerTransportParameters();
      if (delegate != null) {
        params = new QuicTransportParamsImpl(delegate);
        transportParams = params;
      }
    }
    return transportParams;
  }

  @Override
  public QuicConnectionClose closePayload() {
    return closePayload;
  }

  @Override
  protected SocketAddress channelRemoteAddress() {
    return remoteAddress;
  }
}
