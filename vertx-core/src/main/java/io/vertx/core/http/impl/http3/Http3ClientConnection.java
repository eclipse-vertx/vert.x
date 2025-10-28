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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3RequestStreamInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.observability.ClientStreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ClientConnection extends Http3Connection implements HttpClientConnection {

  private final HostAndPort authority;
  private final ClientMetrics<Object, HttpRequest, HttpResponse> metrics;
  private Handler<Void> evictionHandler;
  private final long keepAliveTimeoutMillis;
  private final long creationTimetstamp;
  private long expirationTimestampMillis;

  public Http3ClientConnection(QuicConnectionInternal connection,
                               HostAndPort authority,
                               ClientMetrics<Object, HttpRequest, HttpResponse> metrics,
                               long keepAliveTimeoutMillis,
                               Http3Settings localSettings) {
    super(connection, localSettings);

    this.authority = authority;
    this.metrics = metrics;
    this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
    this.creationTimetstamp = System.currentTimeMillis();
  }

  public void init() {

    super.init();

    io.netty.handler.codec.http3.Http3Settings nSettings = nettyLocalSettings();

    Http3ClientConnectionHandler http3Handler = new Http3ClientConnectionHandler(
      null,
      null,
      null,
      new DefaultHttp3SettingsFrame(nSettings),
      true
    );

    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();

    pipeline.addBefore("handler", "http3", http3Handler);



  }

  @Override
  public MultiMap newHttpRequestHeaders() {
    return new HttpRequestHeaders(new DefaultHttp3Headers());
  }

  @Override
  public long activeStreams() {
    return 0;
  }

  @Override
  public long concurrency() {
    // For now hardcode
    return 10;
  }

  @Override
  protected void handleClosed() {
    Handler<Void> handler = evictionHandler;
    if (handler != null) {
      handler.handle(null);
    }
    super.handleClosed();
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return null;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return null;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    return connection.openStream(context, true, new Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>>() {
      @Override
      public ChannelInitializer<QuicStreamChannel> apply(Consumer<QuicStreamChannel> quicStreamChannelConsumer) {
        return new Http3RequestStreamInitializer() {
          @Override
          protected void initRequestStream(QuicStreamChannel ch) {
            quicStreamChannelConsumer.accept(ch);
          }
        };
      }
    }).map(stream -> {
      QuicStreamInternal streamInternal = (QuicStreamInternal) stream;
      VertxTracer<?, ?> tracer = context.owner().tracer();
      ClientStreamObserver observer;
      if (metrics != null || tracer != null) {
        Object metric;
        if (metrics != null) {
          metric = metrics.init();
        } else {
          metric = null;
        }
        observer = new ClientStreamObserver(context, TracingPolicy.PROPAGATE, metrics, metric, connection.metrics(),
          connection.metric(), tracer, connection.remoteAddress());
      } else {
        observer = null;
      }
      Http3ClientStream http3Stream = new Http3ClientStream(this, streamInternal, context, observer);
      http3Stream.init();
      registerStream(http3Stream);
      return http3Stream;
    });
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public boolean isValid() {
    long now = System.currentTimeMillis();
    return now <= expirationTimestampMillis;
  }

  @Override
  public long creationTimestamp() {
    return creationTimetstamp;
  }

  @Override
  public Object metric() {
    return null;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0;
  }

  @Override
  public String indicatedServerName() {
    return "";
  }

  void refresh() {
    expirationTimestampMillis = keepAliveTimeoutMillis > 0 ? System.currentTimeMillis() + keepAliveTimeoutMillis : Long.MAX_VALUE;
  }
}
