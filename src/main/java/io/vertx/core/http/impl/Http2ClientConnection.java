/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.Map;

import static io.vertx.core.http.HttpHeaders.DEFLATE_GZIP;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnection {

  final Http2Pool http2Pool;
  final HttpClientMetrics metrics;
  final Object metric;
  long streamCount;

  public Http2ClientConnection(Http2Pool http2Pool,
                               ContextImpl context,
                               Channel channel,
                               VertxHttp2ConnectionHandler connHandler,
                               HttpClientMetrics metrics) {
    super(channel, context, connHandler, metrics);
    this.http2Pool = http2Pool;
    this.metrics = metrics;
    this.metric = metrics.connected(remoteAddress(), remoteName());
  }

  @Override
  void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    http2Pool.discard(Http2ClientConnection.this);
  }

  @Override
  void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    super.onGoAwayReceived(lastStreamId, errorCode, debugData);
    http2Pool.discard(Http2ClientConnection.this);
  }

  @Override
  void onStreamClosed(Http2Stream nettyStream) {
    super.onStreamClosed(nettyStream);
    http2Pool.recycle(Http2ClientConnection.this);
  }

  synchronized HttpClientStream createStream() throws Http2Exception {
    Http2Connection conn = handler.connection();
    Http2Stream stream = conn.local().createStream(conn.local().incrementAndGetNextStreamId(), false);
    Http2ClientStream clientStream = new Http2ClientStream(this, stream);
    streams.put(clientStream.stream.id(), clientStream);
    return clientStream;
  }

  @Override
  protected Object metric() {
    return metric;
  }

  @Override
  public boolean isValid() {
    Http2Connection conn = handler.connection();
    return !conn.goAwaySent() && !conn.goAwayReceived();
  }

  @Override
  public synchronized void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    if (stream != null) {
      context.executeFromIO(() -> {
        stream.handleHeaders(headers, endOfStream);
      });
    }
  }

  @Override
  public synchronized void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    if (stream != null) {
      Handler<HttpClientRequest> pushHandler = stream.pushHandler();
      if (pushHandler != null) {
        context.executeFromIO(() -> {
          String rawMethod = headers.method().toString();
          HttpMethod method = HttpUtils.toVertxMethod(rawMethod);
          String uri = headers.path().toString();
          String host = headers.authority() != null ? headers.authority().toString() : null;
          MultiMap headersMap = new Http2HeadersAdaptor(headers);
          Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
          HttpClientRequestPushPromise pushReq = new HttpClientRequestPushPromise(this, promisedStream, http2Pool.client, method, rawMethod, uri, host, headersMap);
          if (metrics.isEnabled()) {
            pushReq.metric(metrics.responsePushed(metric, localAddress(), remoteAddress(), pushReq));
          }
          streams.put(promisedStreamId, pushReq.getStream());
          pushHandler.handle(pushReq);
        });
        return;
      }
    }
    handler.writeReset(promisedStreamId, Http2Error.CANCEL.code());
  }

  static class Http2ClientStream extends VertxHttp2Stream<Http2ClientConnection> implements HttpClientStream {

    private HttpClientRequestBase request;
    private HttpClientResponseImpl response;
    private boolean requestEnded;
    private boolean responseEnded;

    public Http2ClientStream(Http2ClientConnection conn, Http2Stream stream) throws Http2Exception {
      this(conn, null, stream);
    }

    public Http2ClientStream(Http2ClientConnection conn, HttpClientRequestBase request, Http2Stream stream) throws Http2Exception {
      super(conn, stream);
      this.request = request;
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    public int id() {
      return super.id();
    }

    @Override
    void handleEnd(MultiMap trailers) {
      if (conn.metrics.isEnabled()) {
        if (request.exceptionOccurred) {
          conn.metrics.requestReset(request.metric());
        } else {
          conn.metrics.responseEnd(request.metric(), response);
        }
      }
      responseEnded = true;
      // Should use a shared immutable object for CaseInsensitiveHeaders ?
      if (trailers == null) {
        trailers = new CaseInsensitiveHeaders();
      }
      response.handleEnd(null, trailers);
    }

    @Override
    void handleData(Buffer buf) {
      response.handleChunk(buf);
    }

    @Override
    void handleReset(long errorCode) {
      if (responseEnded) {
        return;
      }
      responseEnded = true;
      if (conn.metrics.isEnabled()) {
        conn.metrics.requestReset(request.metric());
      }
      handleException(new StreamResetException(errorCode));
    }

    @Override
    void handleClose() {
      if (!responseEnded) {
        responseEnded = true;
        if (conn.metrics.isEnabled()) {
          conn.metrics.requestReset(request.metric());
        }
        handleException(new VertxException("Connection was closed")); // Put that in utility class
      }
    }

    @Override
    public void checkDrained() {
      synchronized (conn) {
        handleInterestedOpsChanged();
      }
    }

    @Override
    void handleInterestedOpsChanged() {
      if (request instanceof HttpClientRequestImpl && !isNotWritable()) {
        if (!isNotWritable()) {
          ((HttpClientRequestImpl) request).handleDrained();
        }
      }
    }

    @Override
    void handleUnknownFrame(int type, int flags, Buffer buff) {
      response.handleUnknowFrame(new HttpFrameImpl(type, flags, buff));
    }

    void handleHeaders(Http2Headers headers, boolean end) {
      if (response == null || response.statusCode() == 100) {
        int status;
        String statusMessage;
        try {
          status = Integer.parseInt(headers.status().toString());
          statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
        } catch (Exception e) {
          handleException(e);
          writeReset(0x01 /* PROTOCOL_ERROR */);
          return;
        }
        response = new HttpClientResponseImpl(
            request,
            HttpVersion.HTTP_2,
            this,
            status,
            statusMessage,
            new Http2HeadersAdaptor(headers)
        );
        request.handleResponse(response);
        if (end) {
          onEnd();
        }
      } else if (end) {
        onEnd(new Http2HeadersAdaptor(headers));
      }
    }

    void handleException(Throwable exception) {
      if (!requestEnded || response == null) {
        context.executeFromIO(() -> {
          request.handleException(exception);
        });
      }
      if (response != null) {
        context.executeFromIO(() -> {
          response.handleException(exception);
        });
      }
    }

    Handler<HttpClientRequest> pushHandler() {
      return ((HttpClientRequestImpl) request).pushHandler();
    }

    @Override
    public void writeHead(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked) {
      writeHeadWithContent(method, rawMethod, uri, headers, hostHeader, chunked, null, false);
    }

    @Override
    public void writeHeadWithContent(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf content, boolean end) {
      Http2Headers h = new DefaultHttp2Headers();
      h.method(method != HttpMethod.OTHER ? method.name() : rawMethod);
      if (method == HttpMethod.CONNECT) {
        if (hostHeader == null) {
          throw new IllegalArgumentException("Missing :authority / host header");
        }
        h.authority(hostHeader);
      } else {
        h.path(uri);
        h.scheme("https");
        if (hostHeader != null) {
          h.authority(hostHeader);
        }
      }
      if (headers != null && headers.size() > 0) {
        for (Map.Entry<String, String> header : headers) {
          h.add(Http2HeadersAdaptor.toLowerCase(header.getKey()), header.getValue());
        }
      }
      if (conn.http2Pool.client.getOptions().isTryUseCompression() && h.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        h.set(HttpHeaderNames.ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      if (conn.metrics.isEnabled()) {
        request.metric(conn.metrics.requestBegin(conn.metric, conn.localAddress(), conn.remoteAddress(), request));
      }
      writeHeaders(h, end && content == null);
      if (content != null) {
        writeBuffer(content, end);
      } else {
        handlerContext.flush();
      }
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      if (buf == null && end) {
        buf = Unpooled.EMPTY_BUFFER;
      }
      if (buf != null) {
        writeData(buf, end);
      }
      if (end) {
        handlerContext.flush();
      }
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      super.writeFrame(type, flags, payload);
    }

    @Override
    public Context getContext() {
      return context;
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
    }

    @Override
    public boolean isNotWritable() {
      return super.isNotWritable();
    }

    @Override
    public void beginRequest(HttpClientRequestImpl request) {
      this.request = request;
    }

    @Override
    public void endRequest() {
      requestEnded = true;
    }

    @Override
    public void reset(long code) {
      if (!(requestEnded && responseEnded)) {
        requestEnded = true;
        responseEnded = true;
        writeReset(code);
        if (conn.metrics.isEnabled()) {
          conn.metrics.requestReset(request.metric());
        }
      }
    }

    @Override
    public HttpClientConnection connection() {
      return conn;
    }

    @Override
    public NetSocket createNetSocket() {
      return conn.toNetSocket(this);
    }
  }
}
