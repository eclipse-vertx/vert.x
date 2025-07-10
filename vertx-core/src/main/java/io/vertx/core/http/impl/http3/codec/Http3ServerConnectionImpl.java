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

import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.http3.Http3HeadersMultiMap;
import io.vertx.core.http.impl.http3.Http3ServerConnection;
import io.vertx.core.http.impl.http3.Http3ServerStream;
import io.vertx.core.http.impl.http3.Http3StreamBase;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.function.Function;
import java.util.function.Supplier;


/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ServerConnectionImpl extends Http3ConnectionImpl implements HttpServerConnection, Http3ServerConnection {

  private final HttpServerOptions options;
  private final HttpServerMetrics metrics;
  private final Function<String, String> encodingDetector;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final VertxHttp3ConnectionHandler handler;

  private Handler<Http3ServerStream> streamHandler;
  private int concurrentStreams;
//  private final LinkedHashMap<Integer, Pending> pendingPushes = new LinkedHashMap<>();

  public Http3ServerConnectionImpl(
    ContextInternal context,
    Supplier<ContextInternal> streamContextSupplier,
    VertxHttp3ConnectionHandler connHandler,
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
  public Http3ServerConnection streamHandler(Handler<Http3ServerStream> handler) {
    this.streamHandler = handler;
    return this;
  }

  public HttpServerMetrics metrics() {
    return metrics;
  }

  private static class EncodingDetector extends HttpContentCompressor {

    private EncodingDetector(CompressionOptions[] compressionOptions) {
      super(compressionOptions);
    }

    @Override
    protected String determineEncoding(String acceptEncoding) {
      return super.determineEncoding(acceptEncoding);
    }
  }

  public String determineContentEncoding(Http3HeadersMultiMap headers) {
    String acceptEncoding = headers.get(HttpHeaderNames.ACCEPT_ENCODING) != null ? headers.get(HttpHeaderNames.ACCEPT_ENCODING).toString() : null;
    if (acceptEncoding != null && encodingDetector != null) {
      return encodingDetector.apply(acceptEncoding);
    }
    return null;
  }

  private Http3ServerStream createStream(Http3Headers headers, boolean streamEnded) {
    return new Http3ServerStream(
      this,
      metrics,
      metric(),
      streamContextSupplier.get(),
      options.getTracingPolicy()
    );
  }

  private void initStream(QuicStreamChannel streamChannel, Http3ServerStream vertxStream) {
    vertxStream.init(streamChannel, streamChannel.isWritable());
    init_(vertxStream, streamChannel);
  }

/*
  @Override
  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriorityBase streamPriority, boolean endOfStream) {
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
*/

/*  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    super.onRstStreamRead(ctx, streamId, errorCode);
    Pending pendingPush = pendingPushes.remove(streamId);
    if (pendingPush != null) {
      concurrentStreams--;
      checkNextPendingPush();
      pendingPush.promise.fail(new StreamResetException(errorCode));
    }
  }*/

/*
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
*/

/*
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
*/
/*

  public void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriorityBase streamPriority, Promise<Http2ServerStream> promise) {
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

  private synchronized void doSendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriorityBase streamPriority, Promise<Http2ServerStream> promise) {
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
        synchronized (Http3ServerConnectionImpl.this) {
          int promisedStreamId = future.getNow();
          Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
          Http2ServerStream vertxStream = new Http2ServerStream(
            null,
            metrics,
            metric(),
            context,
            new Http2HeadersMultiMap(headers_),
            method,
            path,
            options.getTracingPolicy(),
            promisedStreamId);
//          promisedStream.setProperty(streamKey, vertxStream);
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
*/

  @Override
  protected synchronized void onHeadersRead(Http3StreamBase stream, QuicStreamChannel streamChannel, Http3Headers headers, StreamPriorityBase streamPriority, boolean endOfStream) {
    Http3ServerStream stream0;
    if (stream == null) {
      Http3HeadersMultiMap headersMap = new Http3HeadersMultiMap(headers);
      if (!headersMap.validate(true)) {
        handler.writeReset(streamChannel, Http2Error.PROTOCOL_ERROR.code(), null);
        return;
      }
      headersMap.sanitize();
//      if (streamId == 1 && handler.upgraded) {
//        stream0 = createStream(headers, true);
//      } else {
        stream0 = createStream(headers, endOfStream);
//      }
      initStream(streamChannel, stream0);
      if (streamPriority != null) {
        stream0.priority(streamPriority);
      }

      stream0.context().execute(stream0, streamHandler);
      stream0.onHeaders(headersMap);
    } else {
      // Http server request trailer - not implemented yet (in api)
      stream0 = (Http3ServerStream) stream(streamChannel);
    }
    if (endOfStream) {
      stream0.onTrailers();
    }
  }

  /*
  //TODO: remove block.
  //TODO: it is the old http3 impl
  @Override
  protected void onHeadersRead(Http3StreamBase stream, Http3Headers headers, StreamPriorityBase streamPriority, boolean endOfStream, QuicStreamChannel streamChannel) {
    //TODO: Alter the logic of this method based on onHeadersRead method in the Http2ServerConnection class.
    Http3ServerStream stream0 = null;
    if (stream == null) {
//      if (streamId == 1) {
//        stream = createStream(headers, true);
//        upgraded = stream;
//      } else {
      stream0 = createStream(headers, endOfStream);
//      }
      Http3HeadersMultiMap headersMap = new Http3HeadersMultiMap(headers);

      if (!headersMap.validate(true)) {
//        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code(), null);
        return;
      }
      headersMap.sanitize();

*//*
      if (isMalformedRequest(stream0)) {
//        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code());
        return;
      }
*//*
      initStream(streamChannel, stream0);
      stream0.context().execute(stream0, streamHandler);
      stream0.onHeaders(headersMap);
    } else {
      // Http server request trailer - not implemented yet (in api)
      stream0 = (Http3ServerStream) stream;
    }
    if (endOfStream) {
      stream0.onTrailers();
    }
  }*/

  @Override
  public void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriorityBase streamPriority, Promise<Http3ServerStream> promise) {

  }
}
