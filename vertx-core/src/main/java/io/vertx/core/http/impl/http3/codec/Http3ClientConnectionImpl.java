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

package io.vertx.core.http.impl.http3.codec;

import io.netty.handler.codec.http2.Http2Error;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.http3.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ClientConnectionImpl extends Http3ConnectionImpl implements HttpClientConnection, Http3ClientConnection {

  private final HttpClientBase client;
  private final ClientMetrics metrics;
  private final HostAndPort authority;
  private final boolean pooled;
  private final long lifetimeEvictionTimestamp;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;
  private final VertxHttp3ConnectionHandler handler;

  Http3ClientConnectionImpl(HttpClientBase client,
                            ContextInternal context,
                            HostAndPort authority,
                            VertxHttp3ConnectionHandler connHandler,
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

//  @Override
//  protected void onHeadersRead(Http3StreamBase stream, Http3Headers headers, StreamPriorityBase streamPriority, boolean endOfStream, QuicStreamChannel streamChannel) {
//    stream.determineIfTrailersReceived(new Http3HeadersMultiMap(headers));
//    if (!stream.isTrailersReceived()) {
//      stream.onHeaders(new Http3HeadersMultiMap(headers));
//    } else {
//      stream.onTrailers(new Http3HeadersMultiMap(headers));
//    }
//  }



//  protected synchronized void onHeadersRead(int streamId, Http3Headers headers, StreamPriorityBase streamPriority, boolean endOfStream) {
//    Http3ClientStream stream = (Http3ClientStream) stream(streamId);
//    Http3Stream s = handler.connection().stream(streamId);
//    Http3HeadersMultiMap headersMap = new Http3HeadersMultiMap(headers);
//    if (!s.isTrailersReceived()) {
//      if (!headersMap.validate(false)) {
//        handler.writeReset(streamId, Http3Error.PROTOCOL_ERROR.code(), null);
//      } else {
//        headersMap.sanitize();
//        if (streamPriority != null) {
//          stream.priority(streamPriority);
//        }
//        stream.onHeaders(headersMap);
//        if (endOfStream) {
//          stream.onTrailers();
//        }
//      }
//    } else {
//      stream.onTrailers(headersMap);
//    }
//  }

  @Override
  protected synchronized void onHeadersRead(Http3StreamBase stream, QuicStreamChannel streamChannel, Http3Headers headers, StreamPriorityBase streamPriority, boolean endOfStream) {
    Http3ClientStream stream0 = (Http3ClientStream) stream(streamChannel);
    Http3HeadersMultiMap headersMap = new Http3HeadersMultiMap(headers);
    if (!stream0.isTrailersReceived()) {
      if (!headersMap.validate(false)) {
        handler.writeReset(streamChannel, Http2Error.PROTOCOL_ERROR.code(), null);
      } else {
        headersMap.sanitize();
        if (streamPriority != null) {
          stream0.priority(streamPriority);
        }
        stream0.onHeaders(headersMap);
        if (endOfStream) {
          stream0.onTrailers();
        }
      }
    } else {
      stream0.onTrailers(headersMap);
    }
  }


  @Override
  public void consumeCredits(QuicStreamChannel streamChannel, int amountOfBytes) {
  }

  HttpClientBase client() {
    return client;
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
  public Http3ClientConnectionImpl evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return this;
  }

  @Override
  public Http3ClientConnectionImpl concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  public long concurrency() {
    long concurrency = Math.min(client.options().getSslOptions().getHttp3InitialMaxStreamsBidirectional(),
      client.options().getSslOptions().getHttp3InitialMaxStreamsUnidirectional());
    long http3MaxConcurrency = client.options().getHttp3MultiplexingLimit() <= 0 ? Long.MAX_VALUE :
      client.options().getHttp3MultiplexingLimit();
    if (http3MaxConcurrency > 0) {
      concurrency = Math.min(concurrency / 2, http3MaxConcurrency);
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
    int limit = client.options().getHttp3MultiplexingLimit();
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

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    synchronized (this) {
      try {
        Http3ClientStreamImpl stream = createStream3(context);
        return context.succeededFuture(stream);
      } catch (Exception e) {
        return context.failedFuture(e);
      }
    }
  }

  private Http3ClientStreamImpl createStream3(ContextInternal context) {
    return new Http3ClientStreamImpl(this, context, client.options.getTracingPolicy(), client.options.isDecompressionSupported(), clientMetrics());
  }

  private void recycle() {
    int timeout = client.options().getHttp2KeepAliveTimeout();
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000L : Long.MAX_VALUE;
  }

  @Override
  void onStreamClosed(Http3StreamBase stream) {
    super.onStreamClosed(stream);
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


//  @Override
//  protected void handleIdle(IdleStateEvent event) {
//    if (handler.connection().local().numActiveStreams() > 0) {
//      super.handleIdle(event);
//    }
//  }

  public static VertxHttp3ConnectionHandler<Http3ClientConnectionImpl> createHttp3ConnectionHandler(
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
    VertxHttp3ConnectionHandler<Http3ClientConnectionImpl> handler = new VertxHttp3ConnectionHandlerBuilder<Http3ClientConnectionImpl>()
      .server(false)
//      .useDecompression(client.options().isDecompressionSupported())
//      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .httpSettings(HttpUtils.fromVertxSettings(options.getInitialHttp3Settings()))
      .connectionFactory(connHandler -> {
        Http3ClientConnectionImpl conn = new Http3ClientConnectionImpl(client, context, authority, connHandler, metrics, pooled, maxLifetime);
        if (metrics != null) {
          Object m = socketMetric;
          conn.metric(m);
        }
        return conn;
      })
      .build(context);
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
  public void createStream(Http3ClientStream vertxStream, Handler<QuicStreamChannel> onComplete) throws Exception {
    Future<QuicStreamChannel> streamChannel1 = handler.createStreamChannel();
    streamChannel1.onSuccess(streamChannel -> {
      init_(vertxStream, streamChannel);
      vertxStream.init(streamChannel);
      onComplete.handle(streamChannel);
    }).onFailure(this::handleException);
  }
}
