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
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxDefaultHttp2Headers;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnection {

  public final HttpClientImpl client;
  private final ClientMetrics metrics;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;

  Http2ClientConnection(HttpClientImpl client,
                        EventLoopContext context,
                        VertxHttp2ConnectionHandler connHandler,
                        ClientMetrics metrics) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
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
    long concurrency = remoteSettings().getMaxConcurrentStreams();
    long http2MaxConcurrency = client.options().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.options().getHttp2MultiplexingLimit();
    if (http2MaxConcurrency > 0) {
      concurrency = Math.min(concurrency, http2MaxConcurrency);
    }
    return concurrency;
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

  void upgradeStream(Object metric, Object trace, ContextInternal context, Handler<AsyncResult<HttpClientStream>> completionHandler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        HttpStream stream = createStream(context);
        stream.init(handler.connection().stream(1));
        stream.metric = metric;
        stream.trace = trace;
        stream.requestEnded = true;
        fut = Future.succeededFuture((HttpClientStream) stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    completionHandler.handle(fut);
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

  private HttpStreamImpl<Http2ClientConnection, Http2Stream> createStream(ContextInternal context) {
    return new Http2ClientStream(this, context, false, metrics);
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
    HttpStream stream = (HttpStream) stream(streamId);
    if (!stream.isTrailersReceived()) {
      stream.onHeaders(new VertxDefaultHttp2Headers(headers), streamPriority);
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
        HttpStreamImpl<Http2ClientConnection, Http2Stream> pushStream = new Http2ClientStream(this, context,
          true, metrics);
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
    HttpClientImpl client,
    ClientMetrics metrics,
    EventLoopContext context,
    boolean upgrade,
    Object socketMetric) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
      .server(false)
      .useDecompression(client.options().isTryUseCompression())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(client.options().getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ClientConnection conn = new Http2ClientConnection(client, context, connHandler, metrics);
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
}
