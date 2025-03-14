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

import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class Http3ClientConnection extends Http3ConnectionBase implements HttpClientConnectionInternal {

  public final HttpClientBase client;
  private final ClientMetrics metrics;
  private final HostAndPort authority;
  private final boolean pooled;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;

  public Http3ClientConnection(HttpClientBase client,
                               ContextInternal context,
                               VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> connHandler,
                               ClientMetrics metrics, HostAndPort authority, boolean pooled) {
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
  public Http3ClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnectionInternal invalidMessageHandler(Handler<Object> handler) {
    return this;
  }

  @Override
  public Http3ClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  public long concurrency() {
    long concurrency = Math.min(client.options().getSslOptions().getHttp3InitialMaxStreamsBidirectional(),
      client.options().getSslOptions().getHttp3InitialMaxStreamsUnidirectional());
    long http3MaxConcurrency = client.options().getHttp3MultiplexingLimit() <= 0 ? Long.MAX_VALUE :
      client.options().getHttp3MultiplexingLimit();
    if (http3MaxConcurrency > 0) {
      concurrency = Math.min(concurrency, http3MaxConcurrency);
    }
    return concurrency;
  }

  @Override
  public long activeStreams() {
    return getActiveQuicStreamChannels().size();
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
    long limit = client.options().getSslOptions().getHttp3InitialMaxStreamsBidirectional();
    if (limit > 0) {
      concurrency = Math.min(concurrency, limit);
    }
    concurrencyChangeHandler.handle(concurrency);
  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
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

  private HttpStreamImpl<Http3ClientConnection, QuicStreamChannel> createStream2(ContextInternal context) {
    return new Http3ClientStream(this, context, false);
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

  @Override
  protected synchronized void onHeadersRead(VertxHttpStreamBase<?, ?> stream, Http3Headers headers,
                                            StreamPriorityBase streamPriority, boolean endOfStream,
                                            QuicStreamChannel streamChannel) {
    stream.determineIfTrailersReceived(headers);
    if (!stream.isTrailersReceived()) {
      stream.onHeaders(new Http3HeadersAdaptor(headers), streamPriority);
    } else {
      stream.onEnd(new Http3HeadersAdaptor(headers));
    }
  }

  public void metricsEnd(HttpStream stream) {
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.bytesRead());
    }
  }

  @Override
  protected void handleIdle(IdleStateEvent event) {
//    if (handler.connection().local().numActiveStreams() > 0) {
    super.handleIdle(event);
//    }
  }

  public static VertxHttp3ConnectionHandler<Http3ClientConnection> createVertxHttp3ConnectionHandler(
    HttpClientBase client,
    ClientMetrics metrics,
    ContextInternal context,
    boolean upgrade,
    Object socketMetric,
    HostAndPort authority, boolean pooled) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp3ConnectionHandler<Http3ClientConnection> handler =
      new VertxHttp3ConnectionHandlerBuilder<Http3ClientConnection>()
        .server(false)
        .httpSettings(HttpUtils.fromVertxSettings(client.options().getInitialHttp3Settings()))
        .connectionFactory(connHandler -> {
          Http3ClientConnection conn = new Http3ClientConnection(client, context, connHandler, metrics, authority,
            pooled);
          if (metrics != null) {
            Object m = socketMetric;
            conn.metric(m);
          }
          return conn;
        })
        .build(context);
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (metrics != null) {
        if (!upgrade) {
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
}
