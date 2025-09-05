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
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.http.impl.http2.Http2ServerStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerConnectionImpl extends Http2ConnectionImpl implements HttpServerConnection, Http2ServerConnection {

  private final HttpServerOptions options;
  private final HttpServerMetrics metrics;
  private final Function<String, String> encodingDetector;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final VertxHttp2ConnectionHandler handler;

  private Handler<Http2ServerStream> streamHandler;
  private int concurrentStreams;
  private final LinkedHashMap<Integer, Pending> pendingPushes = new LinkedHashMap<>();

  public Http2ServerConnectionImpl(
    ContextInternal context,
    Supplier<ContextInternal> streamContextSupplier,
    VertxHttp2ConnectionHandler connHandler,
    Function<String, String> encodingDetector,
    HttpServerOptions options,
    HttpServerMetrics metrics) {
    super(context, connHandler);

    this.options = options;
    this.encodingDetector = encodingDetector;
    this.streamContextSupplier = streamContextSupplier;
    this.metrics = metrics;
    this.handler = connHandler;
  }

  @Override
  public Http2ServerConnection streamHandler(Handler<Http2ServerStream> handler) {
    this.streamHandler = handler;
    return this;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  public HttpServerMetrics metrics() {
    return metrics;
  }

  public String determineContentEncoding(Http2HeadersMultiMap headers) {
    String acceptEncoding = headers.get(HttpHeaderNames.ACCEPT_ENCODING) != null ? headers.get(HttpHeaderNames.ACCEPT_ENCODING).toString() : null;
    if (acceptEncoding != null && encodingDetector != null) {
      return encodingDetector.apply(acceptEncoding);
    }
    return null;
  }

  private Http2ServerStream createStream(Http2Headers headers, boolean streamEnded) {
    return new Http2ServerStream(
      this,
      metrics,
      metric(),
      streamContextSupplier.get(),
      options.getTracingPolicy()
    );
  }

  private void initStream(int streamId, Http2ServerStream vertxStream) {
    Http2Stream stream = handler.connection().stream(streamId);
    vertxStream.init(stream.id(), isWritable(streamId));
    stream.setProperty(streamKey, vertxStream);
  }

  @Override
  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    Http2Stream nettyStream = handler.connection().stream(streamId);
    Http2ServerStream stream;
    if (nettyStream.getProperty(streamKey) == null) {
      Http2HeadersMultiMap headersMap = new Http2HeadersMultiMap(headers);
      if (!headersMap.validate(true)) {
        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code(), null);
        return;
      }
      headersMap.sanitize();
      if (streamId == 1 && handler.upgraded) {
        stream = createStream(headers, true);
      } else {
        stream = createStream(headers, endOfStream);
      }
      initStream(streamId, stream);
      if (streamPriority != null) {
        stream.priority(streamPriority);
      }

      stream.context().execute(stream, streamHandler);
      stream.onHeaders(headersMap);
    } else {
      // Http server request trailer - not implemented yet (in api)
      stream = nettyStream.getProperty(streamKey);
    }
    if (endOfStream) {
      stream.onTrailers();
    }
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    super.onRstStreamRead(ctx, streamId, errorCode);
    Pending pendingPush = pendingPushes.remove(streamId);
    if (pendingPush != null) {
      concurrentStreams--;
      checkNextPendingPush();
      pendingPush.promise.fail(new StreamResetException(errorCode));
    }
  }

  private void checkNextPendingPush() {
    int maxConcurrentStreams = handler.maxConcurrentStreams();
    Iterator<Map.Entry<Integer, Pending>> it = pendingPushes.entrySet().iterator();
    while (concurrentStreams < maxConcurrentStreams && it.hasNext()) {
      Map.Entry<Integer, Pending> next = it.next();
      it.remove();
      concurrentStreams++;
      Pending pending = next.getValue();
      Http2ServerStream stream = pending.stream;
      if (!isWritable(stream.id())) {
        stream.onWritabilityChanged();
      }
      pending.promise.complete(stream);
    }
  }

  @Override
  void onStreamClosed(Http2Stream s) {
    super.onStreamClosed(s);
    if (pendingPushes.remove(s.id()) != null) {
      //
    } else {
      concurrentStreams--;
      checkNextPendingPush();
    }
  }

  public void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<Http2ServerStream> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doSendPush(streamId, authority, method, headers, path, streamPriority, promise);
    } else {
      eventLoop.execute(() -> doSendPush(streamId, authority, method, headers, path, streamPriority, promise));
    }
  }

  static class Pending {
    final Http2ServerStream stream;
    final Promise<Http2ServerStream> promise;
    Pending(Http2ServerStream stream, Promise<Http2ServerStream> promise) {
      this.stream = stream;
      this.promise = promise;
    }
  }

  private synchronized void doSendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<Http2ServerStream> promise) {
    boolean ssl = isSsl();
    Http2Headers headers_ = new DefaultHttp2Headers();
    headers_.method(method.name());
    headers_.path(path);
    headers_.scheme(ssl ? "https" : "http");
    if (authority != null) {
      String s = (ssl && authority.port() == 443) || (!ssl && authority.port() == 80) || authority.port() <= 0 ? authority.host() : authority.host() + ':' + authority.port();
      headers_.authority(s);
    }
    if (headers != null) {
      headers.forEach(header -> headers_.add(header.getKey(), header.getValue()));
    }
    Future<Integer> fut = handler.writePushPromise(streamId, headers_);
    fut.addListener((FutureListener<Integer>) future -> {
      if (future.isSuccess()) {
        synchronized (Http2ServerConnectionImpl.this) {
          int promisedStreamId = future.getNow();
          Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
          Http2ServerStream vertxStream = new Http2ServerStream(
            this,
            metrics,
            metric(),
            context,
            new Http2HeadersMultiMap(headers_),
            method,
            path,
            options.getTracingPolicy(),
            promisedStreamId);
          promisedStream.setProperty(streamKey, vertxStream);
          int maxConcurrentStreams = handler.maxConcurrentStreams();
          if (concurrentStreams < maxConcurrentStreams) {
            concurrentStreams++;
            if (!isWritable(promisedStreamId)) {
              vertxStream.onWritabilityChanged();
            }
            promise.complete(vertxStream);
          } else {
            pendingPushes.put(promisedStreamId, new Pending(vertxStream, promise));
          }
        }
      } else {
        promise.fail(future.cause());
      }
    });
  }

  protected io.vertx.core.Future<Void> updateSettings(Http2Settings settingsUpdate) {
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    return super.updateSettings(settingsUpdate);
  }
}
