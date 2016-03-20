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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.Context;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
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
  public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    http2Pool.discard(Http2ClientConnection.this);
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    super.onGoAwayReceived(lastStreamId, errorCode, debugData);
    http2Pool.discard(Http2ClientConnection.this);
  }

  @Override
  public void onStreamClosed(Http2Stream nettyStream) {
    super.onStreamClosed(nettyStream);
    http2Pool.recycle(Http2ClientConnection.this);
  }

  HttpClientStream createStream() {
    try {
      Http2Connection conn = handler.connection();
      Http2Stream stream = conn.local().createStream(conn.local().incrementAndGetNextStreamId(), false);
      Http2ClientStream clientStream = new Http2ClientStream(this, stream);
      streams.put(clientStream.stream.id(), clientStream);
      return clientStream;
    } catch (Http2Exception e) {
      throw new UnsupportedOperationException("handle me gracefully", e);
    }
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
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    throw new UnsupportedOperationException("todo");
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    stream.handleHeaders(headers, endOfStream);
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
  }

  @Override
  public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    HttpMethod method = HttpUtils.toVertxMethod(headers.method().toString());
    String uri = headers.path().toString();
    String host = headers.authority() != null ? headers.authority().toString() : null;
    MultiMap headersMap = new Http2HeadersAdaptor(headers);
    Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
    HttpClientRequestPushPromise promisedReq = new HttpClientRequestPushPromise(this, promisedStream, http2Pool.client, method, uri, host, headersMap);
    if (metrics.isEnabled()) {
      promisedReq.metric(metrics.responsePushed(metric, localAddress(), remoteAddress(), promisedReq));
    }
    streams.put(promisedStreamId, promisedReq.getStream());
    stream.handlePushPromise(promisedReq);
  }

  static class Http2ClientStream extends VertxHttp2Stream<Http2ClientConnection> implements HttpClientStream {

    private HttpClientRequestBase req;
    private HttpClientResponseImpl resp;
    private boolean ended;

    public Http2ClientStream(Http2ClientConnection conn, Http2Stream stream) throws Http2Exception {
      this(conn, null, stream);
    }

    public Http2ClientStream(Http2ClientConnection conn, HttpClientRequestBase req, Http2Stream stream) throws Http2Exception {
      super(conn, stream);
      this.req = req;
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    void callEnd() {
      if (conn.metrics.isEnabled()) {
        HttpClientRequestBase req = this.req;
        if (req.exceptionOccurred) {
          conn.metrics.requestReset(req.metric());
        } else {
          conn.metrics.responseEnd(req.metric(), resp);
        }
      }
      ended = true;
      // Should use a shared immutable object for CaseInsensitiveHeaders ?
      resp.handleEnd(null, new CaseInsensitiveHeaders());
    }

    @Override
    void callHandler(Buffer buf) {
      resp.handleChunk(buf);
    }

    @Override
    void callReset(long errorCode) {
      if (!ended) {
        ended = true;
        if (conn.metrics.isEnabled()) {
          HttpClientRequestBase req = this.req;
          conn.metrics.requestReset(req.metric());
        }
        handleException(new StreamResetException(errorCode));
      }
    }

    @Override
    void handleClose() {
      if (!ended) {
        ended = true;
        if (conn.metrics.isEnabled()) {
          HttpClientRequestBase req = this.req;
          conn.metrics.requestReset(req.metric());
        }
        handleException(new VertxException("Connection was closed")); // Put that in utility class
      }
    }

    @Override
    public void handleInterestedOpsChanged() {
      if (req instanceof HttpClientRequestImpl && !isNotWritable()) {
        if (!isNotWritable()) {
          ((HttpClientRequestImpl)req).handleDrained();
        }
      }
    }

    @Override
    void handleUnknownFrame(int type, int flags, Buffer buff) {
      resp.handleUnknowFrame(new HttpFrameImpl(type, flags, buff));
    }

    void handleHeaders(Http2Headers headers, boolean end) {
      if (resp == null || resp.statusCode() == 100) {
        int status;
        String statusMessage;
        try {
          status = Integer.parseInt(headers.status().toString());
          statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
        } catch (Exception e) {
          handleException(e);
          encoder.writeRstStream(handlerContext, stream.id(), 0x01 /* PROTOCOL_ERROR */, handlerContext.newPromise());
          channel.flush();
          return;
        }
        resp = new HttpClientResponseImpl(
            req,
            HttpVersion.HTTP_2,
            this,
            status,
            statusMessage,
            new Http2HeadersAdaptor(headers)
        );
        req.handleResponse(resp);
        if (end) {
          handleEnd();
        }
      } else if (end) {
        resp.handleEnd(null, new Http2HeadersAdaptor(headers));
      }
    }

    void handleException(Throwable exception) {
      context.executeFromIO(() -> {
        req.handleException(exception);
      });
      if (resp != null) {
        context.executeFromIO(() -> {
          resp.handleException(exception);
        });
      }
    }

    void handlePushPromise(HttpClientRequestBase promised) throws Http2Exception {
      ((HttpClientRequestImpl)req).handlePush(promised);
    }

    @Override
    public void writeHead(HttpMethod method, String uri, MultiMap headers, String hostHeader, boolean chunked) {
      writeHeadWithContent(method, uri, headers, hostHeader, chunked, null, false);
    }

    @Override
    public void writeHeadWithContent(HttpMethod method, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf content, boolean end) {
      if (content != null && content.readableBytes() == 0) {
        content = null;
      }
      Http2Headers h = new DefaultHttp2Headers();
      h.method(method.name());
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
        ((HttpClientRequestImpl)req).metric(conn.metrics.requestBegin(conn.metric, conn.localAddress(), conn.remoteAddress(), req));
      }
      encoder.writeHeaders(handlerContext, stream.id(), h, 0, end && content == null, handlerContext.newPromise());
      if (content != null) {
        writeBuffer(content, end);
      } else {
        channel.flush();
      }
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      writeData(buf, end);
      if (end) {
        channel.flush();
      }
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      encoder.writeFrame(handlerContext, (byte) type, stream.id(), new Http2Flags((short) flags), payload, handlerContext.newPromise());
      handlerContext.flush();
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
      return !conn.handler.connection().remote().flowController().isWritable(stream);
    }
    @Override
    public void beginRequest(HttpClientRequestImpl request) {
      req = request;
    }
    @Override
    public void endRequest() {
    }
    @Override
    public void reset(long code) {
      encoder.writeRstStream(handlerContext, stream.id(), code, handlerContext.newPromise());
      channel.flush();
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
