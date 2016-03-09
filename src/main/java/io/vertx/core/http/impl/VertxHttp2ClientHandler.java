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
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
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
class VertxHttp2ClientHandler extends VertxHttp2ConnectionHandler implements HttpClientConnection {

  final Http2Pool http2Pool;
  private final IntObjectMap<Http2ClientStream> streams = new IntObjectHashMap<>();
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

    encoder.flowController().listener(stream -> {
      Http2ClientStream clientStream = streams.get(stream.id());
      if (clientStream != null && !clientStream.isNotWritable()) {
        clientStream.handleInterestedOpsChanged();
      }
    });
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
    Http2ClientStream stream = streams.remove(nettyStream.id());
    if (!stream.ended) {
      stream.handleException(new VertxException("Connection was closed")); // Put that in utility class
    }
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
  public NetSocket createNetSocket() {
    return null;
  }

  @Override
  public boolean isValid() {
    Http2Connection conn = connection();
    return !conn.goAwaySent() && !conn.goAwayReceived();
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = streams.get(streamId);
    int consumed = stream.handleData(data, endOfStream);
    return consumed + padding;
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    throw new UnsupportedOperationException("todo");
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
    stream.handleReset(errorCode);
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

  @Override
  protected void onStreamError(ChannelHandlerContext ctx, Throwable cause, Http2Exception.StreamException http2Ex) {
    Http2ClientStream stream = streams.get(http2Ex.streamId());
    if (stream != null) {
      context.executeFromIO(() -> {
        stream.handleException(http2Ex);
      });
    }
    // Default behavior reset stream
    super.onStreamError(ctx, cause, http2Ex);
  }

  @Override
  protected void onConnectionError(ChannelHandlerContext ctx, Throwable cause, Http2Exception http2Ex) {
    for (Http2ClientStream stream : streams.values()) {
      context.executeFromIO(() -> {
        stream.handleException(cause);
      });
    }
    super.onConnectionError(ctx, cause, http2Ex);
  }

  static class Http2ClientStream implements HttpClientStream {

    private final VertxHttp2ClientHandler handler;
    private final ContextImpl context;
    private final ChannelHandlerContext handlerCtx;
    private final Http2Connection conn;
    private final Http2Stream stream;
    private final Http2ConnectionEncoder encoder;
    private HttpClientRequestBase req;
    private HttpClientResponseImpl resp;
    private boolean paused;
    private int numBytes;
    private boolean ended;

    public Http2ClientStream(VertxHttp2ClientHandler handler, Http2Stream stream) throws Http2Exception {
      this(handler, null, stream);
    }

    public Http2ClientStream(VertxHttp2ClientHandler handler, HttpClientRequestBase req, Http2Stream stream) throws Http2Exception {
      this.handler = handler;
      this.context = handler.context;
      this.handlerCtx = handler.handlerCtx;
      this.conn = handler.connection();
      this.stream = stream;
      this.encoder = handler.encoder();
      this.req = req;
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

    void handleReset(long errorCode) {
      ended = true;
      handleException(new StreamResetException(errorCode));
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
      ended = true;
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
    public void beginRequest(HttpClientRequestImpl request) {
      req = request;
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
    public HttpClientConnection connection() {
      return handler;
    }

  }
}
