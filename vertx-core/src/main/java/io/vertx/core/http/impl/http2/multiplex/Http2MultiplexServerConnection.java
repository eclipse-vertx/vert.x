/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2.multiplex;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.DefaultHttp2UnknownFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.http.impl.http2.Http2ServerStream;
import io.vertx.core.http.impl.http2.Http2StreamBase;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.HostAndPort;

import java.util.function.Supplier;

public class Http2MultiplexServerConnection extends Http2MultiplexConnection implements Http2ServerConnection, HttpServerConnection {

  private final CompressionManager compressionManager;
  private final String serverOrigin;
  private final boolean handle100ContinueAutomatically;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final Handler<HttpServerRequest> requestHandler;
  private final Handler<HttpServerConnection> connectionHandler;

  public Http2MultiplexServerConnection(Http2MultiplexHandler handler,
                                        CompressionManager compressionManager,
                                        ChannelHandlerContext chctx,
                                        ContextInternal context,
                                        Supplier<ContextInternal> streamContextSupplier,
                                        Handler<HttpServerRequest> requestHandler,
                                        Handler<HttpServerConnection> connectionHandler,
                                        String serverOrigin,
                                        boolean handle100ContinueAutomatically) {
    super(handler, chctx, context);

    this.serverOrigin = serverOrigin;
    this.compressionManager = compressionManager;
    this.streamContextSupplier = streamContextSupplier;
    this.requestHandler = requestHandler;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.connectionHandler = connectionHandler;
  }

  @Override
  boolean isServer() {
    return true;
  }

  // SHOULD UNIFY
  void receiveHeaders(ChannelHandlerContext chctx, Http2FrameStream frameStream, Http2Headers headers, boolean ended) {
    int streamId = frameStream.id();
    StreamChannel channel = channels.get(streamId);
    Http2HeadersMultiMap headersMap = new Http2HeadersMultiMap(headers);
    if (channel == null) {
      if (!headersMap.validate(true)) {
        chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR.code()));
      } else {
        headersMap.sanitize();
        Http2ServerStream stream = createStream(ended);
        stream.init(streamId, chctx.channel().isWritable());
        channel = new StreamChannel(stream, frameStream, chctx);
        channels.put(streamId, channel);
        stream.onHeaders(headersMap);
        if (ended) {
          stream.onTrailers();
        }
      }
    } else {
      channel.stream.onTrailers(headersMap);
    }
  }

  @Override
  void onInitialSettings(io.vertx.core.http.Http2Settings settings) {
    context.emit(this, connectionHandler);
  }

  @Override
  void onStreamClose(int streamId) {
    super.onStreamClose(streamId);
    StreamChannel streamChannel = channels.remove(streamId);
    GoAway goAway = handler.goAwayStatus();
    if (streamChannel != null) {
      Http2StreamBase stream = streamChannel.stream;
      if (goAway != null) {
        stream.onException(new HttpClosedException(goAway));
      }
      stream.onClose();
    }
  }

  @Override
  void onGoAway(long errorCode, int lastStreamId, Buffer debugData) {
    super.onGoAway(errorCode, lastStreamId, debugData);
  }

  Http2ServerStream createStream(boolean ended) {
    return new Http2ServerStream(
            this,
            serverOrigin,
            null,
            null,
            streamContextSupplier.get(),
            requestHandler,
            handle100ContinueAutomatically,
            HttpServerOptions.DEFAULT_MAX_FORM_ATTRIBUTE_SIZE,
            HttpServerOptions.DEFAULT_MAX_FORM_FIELDS,
            HttpServerOptions.DEFAULT_MAX_FORM_BUFFERED_SIZE,
            null,
            ended);
  }

  @Override
  public void writeHeaders(int streamId, Http2HeadersMultiMap headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    Http2HeadersMultiMap prepare = headers.prepare();
    if (headers.status() != null && compressionManager != null) {
      StreamChannel sc = channels.get(streamId);
      compressionManager.setContentEncoding(((Http2ServerStream)sc.stream).headers().unwrap(), headers.unwrap());
    }
    writeStreamFrame(streamId, new DefaultHttp2HeadersFrame((Http2Headers) prepare.unwrap(), end), promise);
  }

  @Override
  public void writePriorityFrame(int streamId, StreamPriority priority) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<Http2ServerStream> promise) {
    promise.fail("Push not supported");
  }

  @Override
  public void flushBytesWritten() {

  }

  @Override
  public void flushBytesRead() {

  }

  @Override
  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    return null;
  }

  @Override
  public HttpServerConnection invalidRequestHandler(Handler<HttpServerRequest> handler) {
    return null;
  }

}
