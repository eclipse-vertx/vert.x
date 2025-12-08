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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.http2.Http2ClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientConnectionImpl extends Http2ConnectionImpl implements HttpClientConnection, Http2ClientConnection {

  private final TransportMetrics<?> transportMetrics;
  private final HttpClientOptions options;
  private final ClientMetrics clientMetrics;
  private final HostAndPort authority;
  private final long lifetimeEvictionTimestamp;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private Handler<AltSvc> alternativeServicesHandler;
  private long expirationTimestamp;
  private boolean evicted;
  private final VertxHttp2ConnectionHandler handler;

  Http2ClientConnectionImpl(ContextInternal context,
                            HostAndPort authority,
                            VertxHttp2ConnectionHandler connHandler,
                            HttpClientMetrics transportMetrics,
                            ClientMetrics clientMetrics,
                            HttpClientOptions options,
                            long maxLifetime) {
    super(context, connHandler);
    this.clientMetrics = clientMetrics;
    this.transportMetrics = transportMetrics;
    this.options = options;
    this.authority = authority;
    this.lifetimeEvictionTimestamp = maxLifetime > 0 ? System.currentTimeMillis() + maxLifetime : Long.MAX_VALUE;
    this.handler = connHandler;
  }

  @Override
  public MultiMap newHttpRequestHeaders() {
    return new HttpResponseHeaders(new DefaultHttp2Headers());
  }

  @Override
  public HostAndPort authority() {
    return authority;
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

  @Override
  public HttpClientConnection alternativeServicesHandler(Handler<AltSvc> handler) {
    alternativeServicesHandler = handler;
    return this;
  }

  public long concurrency() {
    long concurrency = remoteSettings().getMaxConcurrentStreams();
    long http2MaxConcurrency = options.getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : options.getHttp2MultiplexingLimit();
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
    int limit = options.getHttp2MultiplexingLimit();
    if (limit > 0) {
      concurrency = Math.min(concurrency, limit);
    }
    concurrencyChangeHandler.handle(concurrency);
  }

  @Override
  public TransportMetrics<?> metrics() {
    return transportMetrics;
  }

  ClientMetrics clientMetrics() {
    return clientMetrics;
  }

  public HttpClientStream upgradeStream(Object metric, Object trace, ContextInternal context) {
    Http2Stream nettyStream = handler.connection().stream(1);
    Http2ClientStream s = Http2ClientStream.create(nettyStream.id(), this, context, options.getTracingPolicy(),
      options.isDecompressionSupported(), transportMetrics, clientMetrics, isWritable(1));
    s.upgrade(metric, trace);
    nettyStream.setProperty(streamKey, s);
    return s.unwrap();
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    synchronized (this) {
      try {
        Http2ClientStream stream = createStream0(context);
        return context.succeededFuture(stream.unwrap());
      } catch (Exception e) {
        return context.failedFuture(e);
      }
    }
  }

  private Http2ClientStream createStream0(ContextInternal context) {
    return Http2ClientStream.create(
      this,
      context,
      options.getTracingPolicy(),
      options.isDecompressionSupported(),
      transportMetrics,
      clientMetrics());
  }

  private void recycle() {
    int timeout = options.getHttp2KeepAliveTimeout();
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
    HttpResponseHeaders headersMap = new HttpResponseHeaders(headers);
    if (!s.isTrailersReceived()) {
      if (!headersMap.validate()) {
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
//      Http2ClientStreamImpl pushStream = new Http2ClientStreamImpl(this, context, client.options.getTracingPolicy(), client.options.isDecompressionSupported(), clientMetrics());
      Http2ClientStream s = Http2ClientStream.create(this, context, options.getTracingPolicy(), options.isDecompressionSupported(), transportMetrics, clientMetrics);
//      pushStream.init(s);
//      pushStream.stream = s;
      promisedStream.setProperty(streamKey, s);
      HttpRequestHeaders headersMap = new HttpRequestHeaders(headers);
      headersMap.validate();
      headersMap.sanitize();
      stream.onPush(s, promisedStreamId, headersMap, isWritable(promisedStreamId));
    } else {
      Http2ClientConnectionImpl.this.handler.writeReset(promisedStreamId, Http2Error.CANCEL.code(), null);
    }
  }

  @Override
  public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
    if (frameType == 0xA) {
      io.vertx.core.http.impl.http2.Http2Stream stream = stream(streamId);
      AltSvc event = HttpUtils.parseAltSvcFrame(payload);
      Handler<AltSvc> handler;
      if (event != null && (handler = alternativeServicesHandler) != null) {
        if (stream != null) {
          String scheme = stream.scheme();
          HostAndPort authority = stream.authority();
          String host = authority.host();
          int port = authority.port();
          if (port == -1) {
            switch (scheme) {
              case "http":
                port = 80;
                break;
              case "https":
                port = 443;
                break;
            }
          }
          event = new AltSvc(new Origin(scheme, host, port), event.value);
        }
        if (event.origin != null) {
          context.emit(event, handler);
        }
      }
    }
    super.onUnknownFrame(ctx, frameType, streamId, flags, payload);
  }

  @Override
  protected void handleIdle(IdleStateEvent event) {
    if (handler.connection().local().numActiveStreams() > 0) {
      super.handleIdle(event);
    }
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> createHttp2ConnectionHandler(
    HttpClientOptions options,
    HttpClientMetrics clientMetrics,
    ClientMetrics metrics,
    ContextInternal context,
    boolean upgrade,
    Object socketMetric,
    HostAndPort authority,
    long maxLifetime) {
    VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnectionImpl>()
      .server(false)
      .useDecompression(options.isDecompressionSupported())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(options.getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ClientConnectionImpl conn = new Http2ClientConnectionImpl(context, authority, connHandler, clientMetrics, metrics, options, maxLifetime);
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
          clientMetrics.endpointConnected(metrics);
        }
      }
    });
    handler.removeHandler(conn -> {
      if (metrics != null) {
        clientMetrics.endpointDisconnected(metrics);
      }
      conn.tryEvict();
    });
    return handler;
  }

  @Override
  public void createStream(Http2ClientStream stream) throws Exception {
    int id = handler.encoder().connection().local().lastStreamCreated();
    if (id == 0) {
      id = 1;
    } else {
      id += 2;
    }
    Http2Stream nettyStream = handler.encoder().connection().local().createStream(id, false);
    nettyStream.setProperty(streamKey, stream);
    int nettyStreamId = nettyStream.id();
    stream.init(nettyStreamId, isWritable(nettyStream.id()));
  }
}
