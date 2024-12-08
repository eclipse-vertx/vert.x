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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.ArrayDeque;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ServerConnection extends Http3ConnectionBase implements HttpServerConnection {

  final HttpServerOptions options;
  private final String serverOrigin;
  private final HttpServerMetrics metrics;
  private final Function<String, String> encodingDetector;
  private final Supplier<ContextInternal> streamContextSupplier;

  Handler<HttpServerRequest> requestHandler;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>(8);
  private VertxHttpStreamBase<?, ?> upgraded;

  Http3ServerConnection(
    ContextInternal context,
    Supplier<ContextInternal> streamContextSupplier,
    String serverOrigin,
    VertxHttp3ConnectionHandler connHandler,
    Function<String, String> encodingDetector,
    HttpServerOptions options,
    HttpServerMetrics metrics) {
    super(context, connHandler);

    this.options = options;
    this.serverOrigin = serverOrigin;
    this.encodingDetector = encodingDetector;
    this.streamContextSupplier = streamContextSupplier;
    this.metrics = metrics;
  }

  @Override
  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    requestHandler = handler;
    return this;
  }

  @Override
  public HttpServerConnection invalidRequestHandler(Handler<HttpServerRequest> handler) {
    return this;
  }

  public HttpServerMetrics metrics() {
    return metrics;
  }

  private static boolean isMalformedRequest(Http3ServerStream request) {
    if (request.method == null) {
      return true;
    }

    if (request.method == HttpMethod.CONNECT) {
      if (request.scheme != null || request.uri != null || request.authority == null) {
        return true;
      }
    } else {
      if (request.scheme == null || request.uri == null || request.uri.length() == 0) {
        return true;
      }
    }
    if (request.hasAuthority) {
      if (request.authority == null) {
        return true;
      }
      CharSequence hostHeader = request.headers.get(HttpHeaders.HOST);
      if (hostHeader != null) {
        HostAndPort host = HostAndPort.parseAuthority(hostHeader.toString(), -1);
        return host == null || (!request.authority.host().equals(host.host()) || request.authority.port() != host.port());
      }
    }
    return false;
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

  String determineContentEncoding(VertxHttpHeaders headers) {
    String acceptEncoding = headers.get(HttpHeaderNames.ACCEPT_ENCODING) != null ?
      headers.get(HttpHeaderNames.ACCEPT_ENCODING).toString() : null;
    if (acceptEncoding != null && encodingDetector != null) {
      return encodingDetector.apply(acceptEncoding);
    }
    return null;
  }

  private Http3ServerStream createStream(Http3Headers headers, boolean streamEnded) {
    CharSequence schemeHeader = headers.getAndRemove(HttpHeaders.PSEUDO_SCHEME);
    HostAndPort authority = null;
    String authorityHeaderAsString;
    CharSequence authorityHeader = headers.getAndRemove(HttpHeaders.PSEUDO_AUTHORITY);
    if (authorityHeader != null) {
      authorityHeaderAsString = authorityHeader.toString();
      authority = HostAndPort.parseAuthority(authorityHeaderAsString, -1);
    }
    CharSequence pathHeader = headers.getAndRemove(HttpHeaders.PSEUDO_PATH);
    CharSequence methodHeader = headers.getAndRemove(HttpHeaders.PSEUDO_METHOD);
    return new Http3ServerStream(
      this,
      streamContextSupplier.get(),
      new Http3HeadersAdaptor(headers),
      schemeHeader != null ? schemeHeader.toString() : null,
      authorityHeader != null,
      authority,
      methodHeader != null ? HttpMethod.valueOf(methodHeader.toString()) : null,
      pathHeader != null ? pathHeader.toString() : null,
      options.getTracingPolicy(), streamEnded);
  }

  private void initStream(QuicStreamChannel streamChannel, Http3ServerStream vertxStream) {
    String contentEncoding = options.isCompressionSupported() ? determineContentEncoding(vertxStream.headers) : null;
    Http3ServerRequest request = new Http3ServerRequest(vertxStream, serverOrigin, vertxStream.headers,
      contentEncoding);
    vertxStream.request = request;
    vertxStream.isConnect = request.method() == HttpMethod.CONNECT;
    quicStreamChannels.put(streamChannel.streamId(), streamChannel);
    vertxStream.init(streamChannel);
  }

  VertxHttpStreamBase<?, ?> stream(int id) {
    //TODO: this block was commented only to bypass compile exceptions
/*
    VertxHttpStreamBase<?, ?> stream = super.stream(id);
    if (stream == null && id == 1 && handler.upgraded) {
      return upgraded;
    }
    return stream;
*/
    return null;
  }

  @Override
  protected synchronized void onHeadersRead(VertxHttpStreamBase<?, ?> stream, Http3Headers headers,
                                            StreamPriorityBase streamPriority, boolean endOfStream,
                                            QuicStreamChannel streamChannel) {
    //TODO: correct the following block logic
    Http3ServerStream stream0 = null;
    if (stream == null) {
//      if (streamId == 1) {
//        stream = createStream(headers, true);
//        upgraded = stream;
//      } else {
      stream0 = createStream(headers, endOfStream);
//      }
      if (isMalformedRequest(stream0)) {
//        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code());
        return;
      }
      initStream(streamChannel, stream0);
      stream0.onHeaders(new Http3HeadersAdaptor(headers), streamPriority);
    } else {
      // Http server request trailer - not implemented yet (in api)
      stream0 = (Http3ServerStream) stream;
    }
    if (endOfStream) {
      stream0.onEnd();
    }
  }

  void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path,
                StreamPriorityBase streamPriority, Promise<HttpServerResponse> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doSendPush(streamId, authority, method, headers, path, streamPriority, promise);
    } else {
      eventLoop.execute(() -> doSendPush(streamId, authority, method, headers, path, streamPriority, promise));
    }
  }

  private synchronized void doSendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers,
                                       String path, StreamPriorityBase streamPriority,
                                       Promise<HttpServerResponse> promise) {
    boolean ssl = isSsl();
    VertxHttpHeaders headers_ = new Http3HeadersAdaptor();
    headers_.method(method.name());
    headers_.path(path);
    headers_.scheme(ssl ? "https" : "http");
    if (authority != null) {
      String s = (ssl && authority.port() == 443) || (!ssl && authority.port() == 80) || authority.port() <= 0 ?
        authority.host() : authority.host() + ':' + authority.port();
      headers_.authority(s);
    }
    if (headers != null) {
      headers.forEach(header -> headers_.add(header.getKey(), header.getValue()));
    }
    //TODO: this block was commented only to bypass compile exceptions

/*
    Future<Integer> fut = handler.writePushPromise(streamId, headers_);
    fut.addListener((FutureListener<Integer>) future -> {
      if (future.isSuccess()) {
        synchronized (Http3ServerConnection.this) {
          int promisedStreamId = future.getNow();
          String contentEncoding = determineContentEncoding(headers_);
          QuicStreamChannel promisedStream = handler.connection().stream(promisedStreamId);
          Http3ServerStream vertxStream = new Http3ServerStream(this, context, method, path,
            options.getTracingPolicy(), true);
          Push push = new Push(vertxStream, contentEncoding, promise);
          vertxStream.request = push;
          push.stream.priority(streamPriority);
          push.stream.init(promisedStream);
          int maxConcurrentStreams = handler.maxConcurrentStreams();
          if (concurrentStreams < maxConcurrentStreams) {
            concurrentStreams++;
            push.complete();
          } else {
            pendingPushes.add(push);
          }
        }
      } else {
        promise.fail(future.cause());
      }
    });
*/
  }

  protected io.vertx.core.Future<Void> updateSettings(Http2Settings settingsUpdate) {
    //TODO: this block was commented only to bypass compile exceptions

/*
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    return super.updateSettings(settingsUpdate);
*/
    return null;
  }

  private class Push implements Http3ServerStreamHandler {

    protected final ContextInternal context;
    protected final Http3ServerStream stream;
    protected final Http3ServerResponse response;
    private final Promise<HttpServerResponse> promise;

    public Push(Http3ServerStream stream,
                String contentEncoding,
                Promise<HttpServerResponse> promise) {
      this.context = stream.context;
      this.stream = stream;
      this.response = new Http3ServerResponse(stream.conn, stream, true, contentEncoding);
      this.promise = promise;
    }

    @Override
    public Http3ServerResponse response() {
      return response;
    }

    @Override
    public void dispatch(Handler<HttpServerRequest> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void handleReset(long errorCode) {
      if (!promise.tryFail(new StreamResetException(errorCode))) {
        response.handleReset(errorCode);
      }
    }

    @Override
    public void handleException(Throwable cause) {
      if (response != null) {
        response.handleException(cause);
      }
    }

    @Override
    public void handleClose() {
      if (pendingPushes.remove(this)) {
        promise.fail("Push reset by client");
      } else {
        concurrentStreams--;
        //TODO: this block was commented only to bypass compile exceptions
/*
        int maxConcurrentStreams = handler.maxConcurrentStreams();
        while (concurrentStreams < maxConcurrentStreams && pendingPushes.size() > 0) {
          Push push = pendingPushes.pop();
          concurrentStreams++;
          push.complete();
        }
*/
        response.handleClose();
      }
    }

    void complete() {
      stream.registerMetrics();
      promise.complete(response);
    }
  }
}
