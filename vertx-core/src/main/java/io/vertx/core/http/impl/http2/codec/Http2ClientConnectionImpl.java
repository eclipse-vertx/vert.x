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

package io.vertx.core.http.impl.http2.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientBase;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2ClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientStream;
import io.vertx.core.http.impl.http2.Http2ClientStreamImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientConnectionImpl extends Http2ConnectionImpl implements HttpClientConnection, Http2ClientConnection {

  private final HttpClientBase client;
  private final ClientMetrics metrics;
  private final HostAndPort authority;
  private final boolean pooled;
  private final long lifetimeEvictionTimestamp;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;
  private final VertxHttp2ConnectionHandler handler;

  Http2ClientConnectionImpl(HttpClientBase client,
                            ContextInternal context,
                            HostAndPort authority,
                            VertxHttp2ConnectionHandler connHandler,
                            ClientMetrics metrics,
                            boolean pooled,
                            long maxLifetime) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
    this.authority = authority;
    this.pooled = pooled;
    this.lifetimeEvictionTimestamp = maxLifetime > 0 ? System.currentTimeMillis() + maxLifetime : Long.MAX_VALUE;
    this.handler = connHandler;
  }

  HttpClientBase client() {
    return client;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
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
  public Http2ClientConnectionImpl evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return this;
  }

  @Override
  public Http2ClientConnectionImpl concurrencyChangeHandler(Handler<Long> handler) {
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

  ClientMetrics clientMetrics() {
    return metrics;
  }

  public HttpClientStream upgradeStream(Object metric, Object trace, ContextInternal context) {
    Http2ClientStreamImpl vertxStream = createStream2(context);
    Http2Stream nettyStream = handler.connection().stream(1);
    Http2ClientStream s = new Http2ClientStream(nettyStream.id(), this, context, client.options.getTracingPolicy(), client.options.isDecompressionSupported(), clientMetrics(), isWritable(1));
    s.upgrade(metric, trace);
    vertxStream.stream = s;
    s.handler(vertxStream);
    nettyStream.setProperty(streamKey, s);
    return vertxStream;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    synchronized (this) {
      try {
        Http2ClientStreamImpl stream = createStream2(context);
        return context.succeededFuture(stream);
      } catch (Exception e) {
        return context.failedFuture(e);
      }
    }
  }

  private Http2ClientStreamImpl createStream2(ContextInternal context) {
    return new Http2ClientStreamImpl(this, context, client.options.getTracingPolicy(), client.options.isDecompressionSupported(), clientMetrics());
  }

  private void recycle() {
    int timeout = client.options().getHttp2KeepAliveTimeout();
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000L : Long.MAX_VALUE;
  }

  @Override
  void onStreamClosed(Http2Stream s) {
    super.onStreamClosed(s);
    recycle();
  }

  @Override
  public boolean isValid() {
    long now = System.currentTimeMillis();
    return now <= expirationTimestamp && now <= lifetimeEvictionTimestamp;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0L;
  }

  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    Http2ClientStream stream = (Http2ClientStream) stream(streamId);
    Http2Stream s = handler.connection().stream(streamId);
    Http2HeadersMultiMap headersMap = new Http2HeadersMultiMap(headers);
    if (!s.isTrailersReceived()) {
      if (!headersMap.validate(false)) {
        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code(), null);
      } else {
        headersMap.sanitize();
        if (streamPriority != null) {
          stream.priority(streamPriority);
        }
        stream.onHeaders(headersMap);
        if (endOfStream) {
          stream.onTrailers();
        }
      }
    } else {
      stream.onTrailers(headersMap);
    }
  }

  @Override
  public synchronized void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) stream(streamId);
    if (stream != null) {
      Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
      Http2ClientStreamImpl pushStream = new Http2ClientStreamImpl(this, context, client.options.getTracingPolicy(), client.options.isDecompressionSupported(), clientMetrics());
      Http2ClientStream s = new Http2ClientStream(this, context, client.options.getTracingPolicy(), client.options.isDecompressionSupported(), clientMetrics());
      s.handler(pushStream);
      pushStream.stream = s;
      promisedStream.setProperty(streamKey, s);
      Http2HeadersMultiMap headersMap = new Http2HeadersMultiMap(headers);
      headersMap.validate(true);
      headersMap.sanitize();
      stream.onPush(pushStream, promisedStreamId, headersMap, isWritable(promisedStreamId));
    } else {
      Http2ClientConnectionImpl.this.handler.writeReset(promisedStreamId, Http2Error.CANCEL.code(), null);
    }
  }

  @Override
  protected void handleIdle(IdleStateEvent event) {
    if (handler.connection().local().numActiveStreams() > 0) {
      super.handleIdle(event);
    }
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> createHttp2ConnectionHandler(
    HttpClientBase client,
    ClientMetrics metrics,
    ContextInternal context,
    boolean upgrade,
    Object socketMetric,
    HostAndPort authority,
    boolean pooled,
    long maxLifetime) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnectionImpl>()
      .server(false)
      .useDecompression(client.options().isDecompressionSupported())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(client.options().getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ClientConnectionImpl conn = new Http2ClientConnectionImpl(client, context, authority, connHandler, metrics, pooled, maxLifetime);
        if (metrics != null) {
          Object m = socketMetric;
          conn.metric(m);
        }
        return conn;
      })
      .logEnabled(options.getLogActivity())
      .build();
    handler.addHandler(conn -> {
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

  @Override
  public void createStream(Http2ClientStream vertxStream) throws Exception {
    int id = handler.encoder().connection().local().lastStreamCreated();
    if (id == 0) {
      id = 1;
    } else {
      id += 2;
    }
    Http2Stream nettyStream = handler.encoder().connection().local().createStream(id, false);
    nettyStream.setProperty(streamKey, vertxStream);
    int nettyStreamId = nettyStream.id();
    vertxStream.init(nettyStreamId, isWritable(nettyStream.id()));
  }
}
