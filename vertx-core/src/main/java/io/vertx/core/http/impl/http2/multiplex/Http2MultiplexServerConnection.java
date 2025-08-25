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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.http.impl.http2.Http2ServerStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.function.Supplier;

public class Http2MultiplexServerConnection extends Http2MultiplexConnection<Http2ServerStream> implements Http2ServerConnection, HttpServerConnection {

  private final CompressionManager compressionManager;
  private final HttpServerMetrics<?, ?, ?> serverMetrics;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final Handler<HttpServerConnection> connectionHandler;
  private Handler<Http2ServerStream> streamHandler;

  public Http2MultiplexServerConnection(Http2MultiplexHandler handler,
                                        CompressionManager compressionManager,
                                        HttpServerMetrics<?, ?, ?> serverMetrics,
                                        ChannelHandlerContext chctx,
                                        ContextInternal context,
                                        Supplier<ContextInternal> streamContextSupplier,
                                        Handler<HttpServerConnection> connectionHandler) {
    super(handler, serverMetrics, chctx, context);

    this.serverMetrics = serverMetrics;
    this.compressionManager = compressionManager;
    this.streamContextSupplier = streamContextSupplier;
    this.connectionHandler = connectionHandler;
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

  @Override
  boolean isServer() {
    return true;
  }

  // SHOULD UNIFY
  void receiveHeaders(ChannelHandlerContext chctx, Http2FrameStream frameStream, Http2Headers headers, boolean ended) {
    int streamId = frameStream.id();
    Http2ServerStream channel = stream(streamId);
    Http2HeadersMultiMap headersMap = new Http2HeadersMultiMap(headers);
    if (channel == null) {
      if (!headersMap.validate(true)) {
        chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR.code()));
      } else {
        Handler<Http2ServerStream> handler = streamHandler;
        if (handler == null) {
          chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.REFUSED_STREAM.code()));
        } else {
          headersMap.sanitize();
          Http2ServerStream stream = new Http2ServerStream(this, serverMetrics, metric(),
                  streamContextSupplier.get(), null);;
          stream.init(streamId, chctx.channel().isWritable());
          registerChannel(stream, frameStream, chctx);
          ContextInternal streamContext = stream.context();
          streamContext.execute(stream, handler);
          stream.onHeaders(headersMap);
          if (ended) {
            stream.onTrailers();
          }
        }
      }
    } else {
      channel.onTrailers(headersMap);
    }
  }

  @Override
  void onInitialSettingsReceived(io.vertx.core.http.Http2Settings settings) {
    context.emit(this, connectionHandler);
  }

  @Override
  public void writeHeaders(int streamId, Http2HeadersMultiMap headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    Http2HeadersMultiMap prepare = headers.prepare();
    if (headers.status() != null && compressionManager != null) {
      Http2ServerStream stream = stream(streamId);
      compressionManager.setContentEncoding(stream.headers().unwrap(), headers.unwrap());
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
  public Http3Settings http3Settings() {
    throw new UnsupportedOperationException("HTTP/2 connections don't support QUIC");
  }

  @Override
  public Future<Void> updateHttp3Settings(Http3Settings settings) {
    throw new UnsupportedOperationException("HTTP/2 connections don't support QUIC");
  }

  @Override
  public Http3Settings remoteHttp3Settings() {
    throw new UnsupportedOperationException("HTTP/2 connections don't support QUIC");
  }

  @Override
  public HttpConnection remoteHttp3SettingsHandler(Handler<Http3Settings> handler) {
    throw new UnsupportedOperationException("HTTP/2 connections don't support QUIC");
  }
}
