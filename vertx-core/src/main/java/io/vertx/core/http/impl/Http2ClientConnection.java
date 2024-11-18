/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxHttp2Headers;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnectionInternal {

  private final HttpClientBase client;
  private final ClientMetrics metrics;
  private final HostAndPort authority;
  private final boolean pooled;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;

  Http2ClientConnection(HttpClientBase client,
                        ContextInternal context,
                        HostAndPort authority,
                        VertxHttp2ConnectionHandler connHandler,
                        ClientMetrics metrics, boolean pooled) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
    this.authority = authority;
    this.pooled = pooled;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public boolean pooled() {
    return pooled;
  }

  @Override
  public Http2ClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public Http2ClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  public long concurrency() {
    long concurrency = remoteHttpSettings().getMaxConcurrentStreams();
    long http2MaxConcurrency = client.options().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.options().getHttp2MultiplexingLimit();
    if (http2MaxConcurrency > 0) {
      concurrency = Math.min(concurrency, http2MaxConcurrency);
    }
    return concurrency;
  }

  @Override
  public long activeStreams() {
    return handler.connection().numActiveStreams();
  }

  @Override
  boolean onGoAwaySent(GoAway goAway) {
    boolean goneAway = super.onGoAwaySent(goAway);
    if (goneAway) {
      // Eagerly evict from the pool
      tryEvict();
    }
    return goneAway;
  }

  @Override
  boolean onGoAwayReceived(GoAway goAway) {
    boolean goneAway = super.onGoAwayReceived(goAway);
    if (goneAway) {
      // Eagerly evict from the pool
      tryEvict();
    }
    return goneAway;
  }

  /**
   * Try to evict the connection from the pool. This can be called multiple times since
   * the connection can be eagerly removed from the pool on emission or reception of a {@code GOAWAY}
   * frame.
   */
  private void tryEvict() {
    if (!evicted) {
      evicted = true;
      evictionHandler.handle(null);
    }
  }

  @Override
  protected void concurrencyChanged(long concurrency) {
    int limit = client.options().getHttp2MultiplexingLimit();
    if (limit > 0) {
      concurrency = Math.min(concurrency, limit);
    }
    concurrencyChangeHandler.handle(concurrency);
  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  HttpClientStream upgradeStream(Object metric, Object trace, ContextInternal context) throws Exception {
    HttpStreamImpl stream = createStream2(context);
    stream.init(handler.connection().stream(1));
    stream.metric = metric;
    stream.trace = trace;
    stream.requestEnded = true;
    return stream;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    synchronized (this) {
      try {
        HttpStreamImpl stream = createStream2(context);
        return context.succeededFuture(stream);
      } catch (Exception e) {
        return context.failedFuture(e);
      }
    }
  }

  private HttpStreamImpl<Http2ClientConnection, Http2Stream> createStream2(ContextInternal context) {
    return new Http2ClientStream(this, context, false);
  }

  public void recycle() {
    int timeout = client.options().getHttp2KeepAliveTimeout();
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000L : 0L;
  }

  @Override
  public boolean isValid() {
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0L;
  }

  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriorityBase streamPriority, boolean endOfStream) {
    VertxHttpStreamBase<?, ?> stream = stream(streamId);
    if (!stream.isTrailersReceived()) {
      stream.onHeaders(new VertxHttp2Headers(headers), streamPriority);
      if (endOfStream) {
        stream.onEnd();
      }
    } else {
      stream.onEnd(new Http2HeadersAdaptor(headers));
    }
  }

  public void metricsEnd(HttpStream stream) {
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.bytesRead());
    }
  }

  @Override
  public synchronized void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    HttpStreamImpl<Http2ClientConnection, Http2Stream> stream = (HttpStreamImpl) stream(streamId);
    if (stream != null) {
      Handler<HttpClientPush> pushHandler = stream.pushHandler;
      if (pushHandler != null) {
        Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
        HttpStreamImpl<Http2ClientConnection, Http2Stream> pushStream = new Http2ClientStream(this, context, true);
        pushStream.init(promisedStream);
        HttpClientPush push = new HttpClientPush(headers, pushStream);
        if (metrics != null) {
          Object metric = metrics.requestBegin(headers.path().toString(), push);
          pushStream.metric = metric;
          metrics.requestEnd(metric, 0L);
        }
        stream.context.dispatch(push, pushHandler);
        return;
      }
    }

    Http2ClientConnection.this.handler.writeReset(promisedStreamId, Http2Error.CANCEL.code());
  }

  @Override
  protected void handleIdle(IdleStateEvent event) {
    if (handler.connection().local().numActiveStreams() > 0) {
      super.handleIdle(event);
    }
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnection> createHttp2ConnectionHandler(
    HttpClientBase client,
    ClientMetrics metrics,
    ContextInternal context,
    boolean upgrade,
    Object socketMetric,
    HostAndPort authority,
    boolean pooled) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
      .server(false)
      .useDecompression(client.options().isDecompressionSupported())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(client.options().getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ClientConnection conn = new Http2ClientConnection(client, context, authority, connHandler, metrics, pooled);
        if (metrics != null) {
          Object m = socketMetric;
          conn.metric(m);
        }
        return conn;
      })
      .logEnabled(options.getLogActivity())
      .build();
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (metrics != null) {
        if (!upgrade)  {
          met.endpointConnected(metrics);
        }
      }
    });
    handler.removeHandler(conn -> {
      if (metrics != null) {
        met.endpointDisconnected(metrics);
      }
      conn.tryEvict();
    });
    return handler;
  }

  public HttpClientBase client() {
    return client;
  }
}
