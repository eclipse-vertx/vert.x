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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class Http3ClientConnection extends Http3ConnectionBase implements HttpClientConnection {

  public final HttpClientImpl client;
  private final ClientMetrics metrics;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;

  public Http3ClientConnection(HttpClientImpl client,
                               EventLoopContext context,
                               VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> connHandler,
                               ClientMetrics metrics) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
  }

  @Override
  public Http3ClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public Http3ClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  public long concurrency() {
//    long concurrency = remoteSettings().getMaxConcurrentStreams();
//    long http2MaxConcurrency = client.options().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.options().getHttp2MultiplexingLimit();
//    if (http2MaxConcurrency > 0) {
//      concurrency = Math.min(concurrency, http2MaxConcurrency);
//    }
//    return concurrency;
    return 5;
  }

//  @Override
//  boolean onGoAwaySent(GoAway goAway) {
//    boolean goneAway = super.onGoAwaySent(goAway);
//    if (goneAway) {
//      // Eagerly evict from the pool
//      tryEvict();
//    }
//    return goneAway;
//  }
//
//  @Override
//  boolean onGoAwayReceived(GoAway goAway) {
//    boolean goneAway = super.onGoAwayReceived(goAway);
//    if (goneAway) {
//      // Eagerly evict from the pool
//      tryEvict();
//    }
//    return goneAway;
//  }

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

//  @Override
//  protected void concurrencyChanged(long concurrency) {
//    int limit = client.options().getHttp2MultiplexingLimit();
//    if (limit > 0) {
//      concurrency = Math.min(concurrency, limit);
//    }
//    concurrencyChangeHandler.handle(concurrency);
//  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  @Override
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        HttpStreamImpl stream = createStream(context);
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    context.emit(fut, handler);
  }

  private HttpStreamImpl<Http3ClientConnection, QuicStreamChannel, Http3Headers> createStream(ContextInternal context) {
    return new Http3ClientStream(this, context, false, metrics);
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
  protected synchronized void onHeadersRead(VertxHttpStreamBase<?, ?, Http3Headers> stream, Http3Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    if (!stream.isTrailersReceived()) {
      stream.onHeaders(headers, streamPriority);
      if (endOfStream) {
        stream.onEnd();
      }
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
    HttpClientImpl client,
    ClientMetrics metrics,
    EventLoopContext context,
    boolean upgrade,
    Object socketMetric) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp3ConnectionHandler<Http3ClientConnection> handler =
      new VertxHttp3ConnectionHandlerBuilder<Http3ClientConnection>()
        .server(false)
        .http3InitialSettings(client.options().getHttp3InitialSettings())
        .connectionFactory(connHandler -> {
          Http3ClientConnection conn = new Http3ClientConnection(client, context, connHandler, metrics);
          if (metrics != null) {
            Object m = socketMetric;
            conn.metric(m);
          }
          return conn;
        })
        .build(client, metrics, context, socketMetric);
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
}
