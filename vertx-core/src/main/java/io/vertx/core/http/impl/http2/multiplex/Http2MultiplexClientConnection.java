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
package io.vertx.core.http.impl.http2.multiplex;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.http2.Http2ClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

public class Http2MultiplexClientConnection extends Http2MultiplexConnection<Http2ClientStream> implements HttpClientConnection, Http2ClientConnection {

  private final boolean decompressionSupported;
  private final ClientMetrics<?, ?, ?> clientMetrics;
  private final HostAndPort authority;
  private Promise<HttpClientConnection> completion;
  private long concurrency;
  private Handler<Long> concurrencyChangeHandler;
  private Handler<Void> evictionHandler;
  private final long maxConcurrency;
  private final long keepAliveTimeoutMillis;
  private boolean evicted;
  private final long lifetimeEvictionTimestampMillis;
  private long expirationTimestampMillis;

  public Http2MultiplexClientConnection(Http2MultiplexHandler handler,
                                        ChannelHandlerContext chctx,
                                        ContextInternal context,
                                        ClientMetrics<?, ?, ?> clientMetrics,
                                        HttpClientMetrics<?, ?, ?> transportMetrics,
                                        HostAndPort authority,
                                        int maxConcurrency,
                                        long keepAliveTimeoutMillis,
                                        long maxLifetimeMillis,
                                        boolean decompressionSupported,
                                        Promise<HttpClientConnection> completion) {
    super(handler, transportMetrics, chctx, context);

    this.authority = authority;
    this.completion = completion;
    this.clientMetrics = clientMetrics;
    this.concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
    this.maxConcurrency = maxConcurrency < 0 ? Long.MAX_VALUE : maxConcurrency;
    this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
    this.lifetimeEvictionTimestampMillis = maxLifetimeMillis > 0 ? System.currentTimeMillis() + maxLifetimeMillis : Long.MAX_VALUE;
    this.evicted = false;
    this.decompressionSupported = decompressionSupported;
  }

  @Override
  public MultiMap newHttpRequestHeaders() {
    return new HttpResponseHeaders(new DefaultHttp2Headers());
  }

  @Override
  boolean isServer() {
    return false;
  }

  ClientMetrics<?, ?, ?> clientMetrics() {
    return clientMetrics;
  }

  void refresh() {
    expirationTimestampMillis = keepAliveTimeoutMillis > 0 ? System.currentTimeMillis() + keepAliveTimeoutMillis : Long.MAX_VALUE;
  }

  private long actualConcurrency(Http2Settings settings) {
    return Math.min(settings.getMaxConcurrentStreams(), maxConcurrency);
  }

  void onInitialSettingsSent() {
  }

  @Override
  void onInitialSettingsReceived(Http2Settings settings) {
    concurrency = actualConcurrency(settings);
    Promise<HttpClientConnection> c = completion;
    completion = null;
    c.complete(this);
  }

  @Override
  void onSettings(Http2Settings settings) {
    long newConcurrency = actualConcurrency(settings);
    if (newConcurrency != concurrency) {
      Handler<Long> handler = concurrencyChangeHandler;
      if (handler != null) {
        context.emit(newConcurrency, handler);
      }
    }
    concurrency = newConcurrency;
    super.onSettings(settings);
  }

  @Override
  void receiveHeaders(ChannelHandlerContext chctx, Http2FrameStream frameStream, Http2Headers headers, boolean ended) {
    int streamId = frameStream.id();
    Http2ClientStream stream = stream(streamId);
    HttpResponseHeaders headersMap = new HttpResponseHeaders(headers);
    if (!stream.isHeadersReceived()) {
      if (!headersMap.validate()) {
        chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR.code()));
      } else {
        headersMap.sanitize();
        stream.onHeaders(headersMap);
        if (ended) {
          stream.onTrailers();
        }
      }
    } else {
      stream.onTrailers(headersMap);
    }
  }

  @Override
  public long activeStreams() {
    Http2FrameCodec frameCodec = chctx.pipeline().get(Http2FrameCodec.class);
    return frameCodec.connection().numActiveStreams();
  }

  @Override
  public long concurrency() {
    return concurrency;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    this.evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return this;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    Http2ClientStream stream = Http2ClientStream.create(this, context, null, decompressionSupported, transportMetrics, clientMetrics);
    return context.succeededFuture(stream.unwrap());
  }

  @Override
  public boolean isValid() {
    long now = System.currentTimeMillis();
    return now <= expirationTimestampMillis && now <= lifetimeEvictionTimestampMillis;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createStream(Http2ClientStream stream) throws Exception {
    handler.createClientStream(stream);
  }

  @Override
  public void writeHeaders(int streamId, Headers<CharSequence, CharSequence, ?> headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    writeStreamFrame(streamId, new DefaultHttp2HeadersFrame((Http2Headers) headers, end), promise);
  }

  @Override
  public void writePriorityFrame(int streamId, StreamPriority priority, Promise<Void> promise) {
    throw new UnsupportedOperationException();
  }

  @Override
  void onGoAway(long errorCode, int lastStreamId, Buffer debugData) {
    if (!evicted) {
      evicted = true;
      Handler<Void> handler = evictionHandler;
      if (handler != null) {
        context.emit(handler);
      }
    }
    super.onGoAway(errorCode, lastStreamId, debugData);
  }

  @Override
  void onIdle() {
    if (numberOfChannels() > 0) {
      super.onIdle();
    }
  }

  @Override
  void onClose() {
    super.onClose();
    Promise<HttpClientConnection> promise = completion;
    if (promise != null) {
      completion = null;
      promise.fail(ConnectionBase.CLOSED_EXCEPTION);
    }
  }
}
