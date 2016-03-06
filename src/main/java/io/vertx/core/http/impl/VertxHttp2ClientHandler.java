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
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.net.NetSocket;

import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ClientHandler extends VertxHttp2ConnectionHandler {

  final Http2Pool http2Pool;
  final ChannelHandlerContext handlerCtx;
  final ContextImpl context;
  private final IntObjectMap<Http2ClientStream> streams = new IntObjectHashMap<>();

  public VertxHttp2ClientHandler(Http2Pool http2Pool,
                                 ChannelHandlerContext handlerCtx,
                                 ContextImpl context,
                                 Channel channel,
                                 Http2ConnectionDecoder decoder,
                                 Http2ConnectionEncoder encoder,
                                 Http2Settings initialSettings) {
    super(handlerCtx, channel, context, decoder, encoder, initialSettings);
    this.http2Pool = http2Pool;

    encoder.flowController().listener(stream -> {
      Http2ClientStream clientStream = streams.get(stream.id());
      if (clientStream != null && !clientStream.isNotWritable()) {
        clientStream.handleInterestedOpsChanged();
      }
    });

    this.handlerCtx = handlerCtx;
    this.context = context;
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData, Handler<Void> completionHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection shutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection shutdown(long timeout) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public io.vertx.core.http.Http2Settings settings() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings, Handler<AsyncResult<Void>> completionHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public io.vertx.core.http.Http2Settings remoteSettings() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<io.vertx.core.http.Http2Settings> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Handler<io.vertx.core.http.Http2Settings> remoteSettingsHandler() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    throw new UnsupportedOperationException();
  }

  void handle(Handler<HttpClientStream> handler, HttpClientRequestImpl req) {
    Http2ClientStream stream = createStream(req);
    handler.handle(stream);
  }

  Http2ClientStream createStream(HttpClientRequestBase req) {
    try {
      Http2Connection conn = connection();
      Http2Stream stream = conn.local().createStream(conn.local().incrementAndGetNextStreamId(), false);
      Http2ClientStream clientStream = new Http2ClientStream(this, req, stream);
      streams.put(clientStream.stream.id(), clientStream);
      return clientStream;
    } catch (Http2Exception e) {
      throw new UnsupportedOperationException("handle me gracefully", e);
    }
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = streams.get(streamId);
    int consumed = stream.handleData(data, endOfStream);
    return consumed + padding;
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = streams.get(streamId);
    stream.handleHeaders(headers, endOfStream);
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
    Http2ClientStream stream = streams.get(streamId);
    stream.handleException(new StreamResetException(errorCode));
  }

  @Override
  public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    Http2ClientStream stream = streams.get(streamId);
    HttpMethod method = UriUtils.toVertxMethod(headers.method().toString());
    String uri = headers.path().toString();
    String host = headers.authority() != null ? headers.authority().toString() : null;
    MultiMap headersMap = new Http2HeadersAdaptor(headers);
    Http2Stream promisedStream = connection().stream(promisedStreamId);
    HttpClientRequestPushPromise promisedReq = new HttpClientRequestPushPromise(this, promisedStream, http2Pool.client, method, uri, host, headersMap);
    streams.put(promisedStreamId, promisedReq.getStream());
    stream.handlePushPromise(promisedReq);
  }

  static class Http2ClientStream implements HttpClientStream {

    private final VertxHttp2ClientHandler handler;
    private final HttpClientRequestBase req;
    private final ContextImpl context;
    private final ChannelHandlerContext handlerCtx;
    private final Http2Connection conn;
    private final Http2Stream stream;
    private final Http2ConnectionEncoder encoder;
    private HttpClientResponseImpl resp;
    private boolean paused;
    private int numBytes;

    public Http2ClientStream(VertxHttp2ClientHandler handler,
                             HttpClientRequestBase req,
                             Http2Stream stream) throws Http2Exception {
      this.handler = handler;
      this.context = handler.context;
      this.req = req;
      this.handlerCtx = handler.handlerCtx;
      this.conn = handler.connection();
      this.stream = stream;
      this.encoder = handler.encoder();
    }

    void handleHeaders(Http2Headers headers, boolean end) {
      if (resp == null) {
        resp = new HttpClientResponseImpl(
            req,
            HttpVersion.HTTP_2,
            this,
            Integer.parseInt(headers.status().toString()),
            "todo",
            new Http2HeadersAdaptor(headers)
        );
        req.handleResponse(resp);
        if (end) {
          handleEnd();
        }
      } else if (end) {
        resp.handleEnd(new Http2HeadersAdaptor(headers));
      }
    }

    int handleData(ByteBuf chunk, boolean end) {
      int consumed = 0;
      if (chunk.isReadable()) {
        Buffer buff = Buffer.buffer(chunk.slice());
        resp.handleChunk(buff);
        if (paused) {
          numBytes += chunk.readableBytes();
        } else {
          consumed = chunk.readableBytes();
        }
      }
      if (end) {
        handleEnd();
      }
      return consumed;
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

    private void handleEnd() {
      // Should use an shared immutable object ?
      resp.handleEnd(new CaseInsensitiveHeaders());
    }

    void handlePushPromise(HttpClientRequestBase promised) throws Http2Exception {
      ((HttpClientRequestImpl)req).handlePush(promised);
    }

    @Override
    public void writeHead(HttpMethod method, String uri, MultiMap headers, String hostHeader, boolean chunked) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeHeadWithContent(HttpMethod method, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf buf, boolean end) {
      Http2Headers h = new DefaultHttp2Headers();
      h.method(method.name());
      h.path(uri);
      h.scheme("https");
      if (hostHeader != null) {
        h.authority(hostHeader);
      }
      if (headers != null && headers.size() > 0) {
        for (Map.Entry<String, String> header : headers) {
          h.add(Http2HeadersAdaptor.toLowerCase(header.getKey()), header.getValue());
        }
      }
      encoder.writeHeaders(handlerCtx, stream.id(), h, 0, end && buf == null, handlerCtx.newPromise());
      if (buf != null) {
        writeBuffer(buf, end);
      } else {
        handlerCtx.flush();
      }
    }
    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      encoder.writeData(handlerCtx, stream.id(), buf, 0, end, handlerCtx.newPromise());
      if (end) {
        try {
          encoder.flowController().writePendingBytes();
        } catch (Http2Exception e) {
          e.printStackTrace();
        }
      }
      handlerCtx.flush();
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
    public void handleInterestedOpsChanged() {
      ((HttpClientRequestImpl)req).handleDrained();
    }
    @Override
    public void endRequest() {
    }
    @Override
    public void doPause() {
      paused = true;
    }
    @Override
    public void doResume() {
      paused = false;
      if (numBytes > 0) {
        int pending = numBytes;
        context.runOnContext(v -> {
          // DefaultHttp2LocalFlowController requires to do this from the event loop
          try {
            boolean windowUpdateSent = conn.local().flowController().consumeBytes(stream, pending);
            if (windowUpdateSent) {
              handlerCtx.flush();
            }
          } catch (Http2Exception e) {
            e.printStackTrace();
          }
        });
        numBytes = 0;
      }
    }
    @Override
    public void reset(long code) {
      encoder.writeRstStream(handlerCtx, stream.id(), code, handlerCtx.newPromise());
      handlerCtx.flush();
    }
    @Override
    public void reportBytesWritten(long numberOfBytes) {
    }
    @Override
    public void reportBytesRead(long s) {
    }
    @Override
    public NetSocket createNetSocket() {
      throw new UnsupportedOperationException();
    }

    @Override
    public HttpConnection connection() {
      return handler;
    }
  }
}
