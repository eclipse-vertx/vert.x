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
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ConnectionGroup;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicConnectionImpl extends ConnectionBase implements QuicConnectionInternal {

  private final ContextInternal context;
  private final QuicChannel channel;
  private final TransportMetrics metrics;
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
  private int maxDatagramLength;
  private final Map<QuicStreamType, StreamOpenRequestQueue> pendingStreamOpenRequestsMap;

  public QuicConnectionImpl(ContextInternal context, TransportMetrics metrics, long idleTimeout,
                            long readIdleTimeout,  long writeIdleTimeout, ByteBufFormat activityLogging, int maxStreamBidiRequests,
                            int maxStreamUniRequests, QuicChannel channel, SocketAddress remoteAddress, ChannelHandlerContext chctx) {
    super(context, chctx);

    Map<QuicStreamType, StreamOpenRequestQueue> pendingStreamRequestsMap = new EnumMap<>(QuicStreamType.class);
    pendingStreamRequestsMap.put(QuicStreamType.BIDIRECTIONAL, new StreamOpenRequestQueue(maxStreamBidiRequests));
    pendingStreamRequestsMap.put(QuicStreamType.UNIDIRECTIONAL, new StreamOpenRequestQueue(maxStreamUniRequests));

    this.channel = channel;
    this.metrics = metrics;
    this.idleTimeout = idleTimeout;
    this.readIdleTimeout = readIdleTimeout;
    this.writeIdleTimeout = writeIdleTimeout;
    this.activityLogging = activityLogging;
    this.context = context;
    this.remoteAddress = remoteAddress;
    this.pendingStreamOpenRequestsMap = pendingStreamRequestsMap;
    this.maxDatagramLength = 0;
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
      this.streamMetrics = new TransportMetrics<>() {
        @Override
        public void bytesRead(Object connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
          metrics.bytesRead(metric(), remoteAddress, numberOfBytes);
        }
        @Override
        public void bytesWritten(Object connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
          metrics.bytesWritten(metric(), remoteAddress, numberOfBytes);
        }
        @Override
        public void disconnected(Object connectionMetric, SocketAddress remoteAddress) {
          metrics.streamClosed(metric());
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
      if (metrics != null) {
        metrics.streamOpened(metric());
      }
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

  void enableDatagramExtension(int maxDatagramLength) {
    this.maxDatagramLength = Math.max(0, maxDatagramLength);
  }

  void handleQuicStreamLimitChanged() {
    trySatisfyPendingStreamOpenRequests(QuicStreamType.BIDIRECTIONAL);
    trySatisfyPendingStreamOpenRequests(QuicStreamType.UNIDIRECTIONAL);
  }

  private void trySatisfyPendingStreamOpenRequests(QuicStreamType type) {
    long maxBidStreams = channel.peerAllowedStreams(type);
    Deque<StreamOpenRequest> pendingStreamOpenRequests = pendingStreamOpenRequestsMap.get(type);
    StreamOpenRequest streamOpenRequest;
    while (maxBidStreams > 0 && (streamOpenRequest = pendingStreamOpenRequests.poll()) != null) {
      maxBidStreams--;
      openStream(streamOpenRequest, type);
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
    for (Deque<StreamOpenRequest> pendingStreamOpenRequests : pendingStreamOpenRequestsMap.values()) {
      StreamOpenRequest pendingStreamOpenRequest;
      while ((pendingStreamOpenRequest = pendingStreamOpenRequests.poll()) != null) {
        pendingStreamOpenRequest.promise.fail(NetSocketInternal.CLOSED_EXCEPTION);
      }
    }
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
  public Future<QuicStream> openStream(ContextInternal streamContext, boolean bidirectional, Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>> initializerProvider) {
    Promise<QuicStream> promise = streamContext.promise();
    StreamOpenRequest streamOpenRequest = new StreamOpenRequest(streamContext, initializerProvider, promise);
    if (context.inThread()) {
      tryOpenStream(streamOpenRequest, bidirectional ? QuicStreamType.BIDIRECTIONAL : QuicStreamType.UNIDIRECTIONAL);
    } else {
      context.execute(() -> {
        tryOpenStream(streamOpenRequest, bidirectional ? QuicStreamType.BIDIRECTIONAL : QuicStreamType.UNIDIRECTIONAL);
      });
    }
    return promise.future();
  }

  private void tryOpenStream(StreamOpenRequest streamOpenRequest, QuicStreamType type) {
    long allowedStreams = channel.peerAllowedStreams(type);
    if (allowedStreams == 0) {
      StreamOpenRequestQueue streamOpenRequests = pendingStreamOpenRequestsMap.get(type);
      if (!streamOpenRequests.offerLast(streamOpenRequest)) {
        streamOpenRequest.promise.fail(new VertxException("QUIC connection pending stream request limit reached"));
      }
    } else {
      openStream(streamOpenRequest, type);
    }
  }

  private void openStream(StreamOpenRequest streamOpenRequest, QuicStreamType streamType) {
    VertxHandler<QuicStreamImpl> handler = VertxHandler.create(chctx -> new QuicStreamImpl(this, streamOpenRequest.context, (QuicStreamChannel) chctx.channel(), streamMetrics, chctx));
    handler.addHandler(stream -> {
      if (metrics != null) {
        metrics.streamOpened(metric());
      }
      streamOpenRequest.promise.tryComplete(stream);
    });
    ChannelInitializer<QuicStreamChannel> initializer = streamOpenRequest.initializerProvider.apply(ch -> {
      ChannelPipeline pipeline = ch.pipeline();
      configureStreamChannelPipeline(pipeline);
      pipeline.addLast("handler", handler);
      streamGroup.add(ch);
    });
    io.netty.util.concurrent.Future<QuicStreamChannel> future = channel.createStream(streamType, initializer);
    future.addListener(f -> {
      if (!f.isSuccess()) {
        streamOpenRequest.promise.tryFail(f.cause());
      }
    });
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
  public SSLSession sslSession() {
    return channel.sslEngine().getSession();
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
  public int maxDatagramLength() {
    return maxDatagramLength;
  }

  @Override
  public QuicConnectionClose closePayload() {
    return closePayload;
  }

  @Override
  protected SocketAddress channelRemoteAddress() {
    return remoteAddress;
  }

  /**
   * A queue of stream open requests.
   */
  private static class StreamOpenRequestQueue extends ArrayDeque<StreamOpenRequest> {

    final int maxSize;

    StreamOpenRequestQueue(int maxSize) {
      super(10);
      this.maxSize = maxSize;
    }

    @Override
    public boolean offerLast(StreamOpenRequest request) {
      if (size() < maxSize) {
        return super.offerLast(request);
      } else {
        return false;
      }
    }
  }

  /**
   * A request to open a stream.
   */
  private static class StreamOpenRequest {

    private final ContextInternal context;
    private final Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>> initializerProvider;
    private final Promise<QuicStream> promise;

    StreamOpenRequest(ContextInternal context, Function<Consumer<QuicStreamChannel>,
      ChannelInitializer<QuicStreamChannel>> initializerProvider, Promise<QuicStream> promise) {
      this.context = context;
      this.initializerProvider = initializerProvider;
      this.promise = promise;
    }
  }
}
