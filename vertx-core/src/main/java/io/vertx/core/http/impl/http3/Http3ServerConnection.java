/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http3;

import io.netty.channel.*;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Handler;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.observability.ServerStreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ServerConnection extends Http3Connection implements HttpServerConnection {

  private final Supplier<ContextInternal> streamContextProvider;
  private final HttpServerMetrics<?, ?> httpMetrics;
  private Handler<HttpServerStream> streamHandler;
  private QuicStreamChannel outboundControlStream;

  public Http3ServerConnection(QuicConnectionInternal connection,
                               Http3Settings localSettings,
                               HttpServerMetrics<?, ?> httpMetrics) {
    super(connection, localSettings);

    this.streamContextProvider = connection.context()::duplicate;
    this.httpMetrics = httpMetrics;
  }

  void handleStream(QuicStreamInternal quicStream) {
    ContextInternal streamContext = streamContextProvider.get();
    VertxTracer<?, ?> tracer = context.owner().tracer();
    ServerStreamObserver observer;
    if (httpMetrics != null || tracer != null) {
      observer = new ServerStreamObserver(streamContext, httpMetrics, null, tracer, connection.metric(),
        TracingPolicy.PROPAGATE, connection.remoteAddress(), HttpVersion.HTTP_3);
    } else {
      observer = null;
    }
    Http3ServerStream httpStream = new Http3ServerStream(this, quicStream, streamContext, observer);
    httpStream.init();
    registerStream(httpStream);
    Handler<HttpServerStream> handler = streamHandler;
    streamContext.emit(httpStream, handler);
  }

  public void init() {

    super.init();

    io.netty.handler.codec.http3.Http3Settings nSettings = nettyLocalSettings();

    Http3ServerConnectionHandler http3Handler = new Http3ServerConnectionHandler(
      new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel ch) {
          // Nothing to do
        }
      },
      new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel ch) {
          outboundControlStream = ch;
        }
      },
      null,
      new DefaultHttp3SettingsFrame(nSettings),
      true
    );

    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();
    pipeline.addBefore("handler", "http3", http3Handler);
  }

  @Override
  public HttpServerConnection streamHandler(Handler<HttpServerStream> handler) {
    streamHandler = handler;
    return this;
  }

  @Override
  public Headers<CharSequence, CharSequence, ?> newHeaders() {
    return new DefaultHttp3Headers();
  }

  @Override
  public boolean supportsSendFile() {
    return false;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return connection.channelHandlerContext();
  }

  @Override
  public String indicatedServerName() {
    return connection.indicatedServerName();
  }
}
