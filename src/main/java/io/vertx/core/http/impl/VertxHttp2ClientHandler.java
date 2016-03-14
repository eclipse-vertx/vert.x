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
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
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

import java.util.Map;

import static io.vertx.core.http.HttpHeaders.DEFLATE_GZIP;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ClientHandler extends VertxHttp2ConnectionHandler implements HttpClientConnection {

  final Http2Pool http2Pool;
  long streamCount;

  public VertxHttp2ClientHandler(Http2Pool http2Pool,
                                 ChannelHandlerContext handlerCtx,
                                 ContextImpl context,
                                 Channel channel,
                                 Http2ConnectionDecoder decoder,
                                 Http2ConnectionEncoder encoder,
                                 Http2Settings initialSettings) {
    super(handlerCtx, channel, context, decoder, encoder, initialSettings);
    this.http2Pool = http2Pool;
  }

  @Override
  public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    http2Pool.discard(VertxHttp2ClientHandler.this);
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    super.onGoAwayReceived(lastStreamId, errorCode, debugData);
    http2Pool.discard(VertxHttp2ClientHandler.this);
  }

  @Override
  public void onStreamClosed(Http2Stream nettyStream) {
    super.onStreamClosed(nettyStream);
    http2Pool.recycle(VertxHttp2ClientHandler.this);
  }

  @Override
  public Context getContext() {
    return context;
  }

  HttpClientStream createStream() {
    try {
      Http2Connection conn = connection();
      Http2Stream stream = conn.local().createStream(conn.local().incrementAndGetNextStreamId(), false);
      Http2ClientStream clientStream = new Http2ClientStream(this, stream);
      streams.put(clientStream.stream.id(), clientStream);
      return clientStream;
    } catch (Http2Exception e) {
      throw new UnsupportedOperationException("handle me gracefully", e);
    }
  }

  @Override
  public void reportBytesWritten(long numberOfBytes) {
  }

  @Override
  public void reportBytesRead(long s) {
  }

  @Override
  public boolean isValid() {
    Http2Connection conn = connection();
    return !conn.goAwaySent() && !conn.goAwayReceived();
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    VertxHttp2Stream stream = streams.get(streamId);
    stream.handleData(Buffer.buffer(data.copy()));
    if (endOfStream) {
      stream.handleEnd();
    }
    return 0;
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
    Http2Stream promisedStream = connection().stream(promisedStreamId);
    HttpClientRequestPushPromise promisedReq = new HttpClientRequestPushPromise(this, promisedStream, http2Pool.client, method, uri, host, headersMap);
    streams.put(promisedStreamId, promisedReq.getStream());
    stream.handlePushPromise(promisedReq);
  }

  static class Http2ClientStream extends VertxHttp2Stream implements HttpClientStream {

    private final VertxHttp2ClientHandler handler;
    private final ContextImpl context;
    private final ChannelHandlerContext handlerCtx;
    private final Http2Connection conn;
    private final Http2Stream stream;
    private HttpClientRequestBase req;
    private HttpClientResponseImpl resp;
    private boolean ended;

    public Http2ClientStream(VertxHttp2ClientHandler handler, Http2Stream stream) throws Http2Exception {
      this(handler, null, stream);
    }

    public Http2ClientStream(VertxHttp2ClientHandler handler, HttpClientRequestBase req, Http2Stream stream) throws Http2Exception {
      super(handler.http2Pool.client.getVertx(), handler.context, handler.handlerCtx, handler.encoder(), handler.decoder(), stream);
      this.handler = handler;
      this.context = handler.context;
      this.handlerCtx = handler.handlerCtx;
      this.conn = handler.connection();
      this.stream = stream;
      this.req = req;
    }

    @Override
    void callEnd() {
      // Should use an shared immutable object ?
      ended = true;
      resp.handleEnd(null, new CaseInsensitiveHeaders());
    }

    @Override
    void callHandler(Buffer buf) {
      resp.handleChunk(buf);
    }

    @Override
    void callReset(long errorCode) {
      ended = true;
      handleException(new StreamResetException(errorCode));
    }

    @Override
    void handleClose() {
      if (!ended) {
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

    void handleHeaders(Http2Headers headers, boolean end) {
      if (resp == null || resp.statusCode() == 100) {
        int status;
        String statusMessage;
        try {
          status = Integer.parseInt(headers.status().toString());
          statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
        } catch (Exception e) {
          handleException(e);
          encoder.writeRstStream(handlerCtx, stream.id(), 0x01 /* PROTOCOL_ERROR */, handlerCtx.newPromise());
          handlerCtx.flush();
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
      if (handler.http2Pool.client.getOptions().isTryUseCompression() && h.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        h.set(HttpHeaderNames.ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      encoder.writeHeaders(handlerCtx, stream.id(), h, 0, end && content == null, handlerCtx.newPromise());
      if (content != null) {
        writeBuffer(content, end);
      } else {
        handlerCtx.flush();
      }
    }
    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      writeData(buf, end);
      if (end) {
        handlerContext.flush();
      }
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
      return !conn.remote().flowController().isWritable(stream);
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
      encoder.writeRstStream(handlerCtx, stream.id(), code, handlerCtx.newPromise());
      handlerCtx.flush();
    }

    @Override
    public HttpClientConnection connection() {
      return handler;
    }

    @Override
    public NetSocket createNetSocket() {
      VertxHttp2NetSocket socket = new VertxHttp2NetSocket(vertx, context, handlerContext, encoder, decoder, stream);
      handler.streams.put(stream.id(), socket);
      return socket;
    }
  }
}
