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
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.*;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.http.impl.http2.Http2ServerStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.util.function.Supplier;

public class Http2MultiplexServerConnection extends Http2MultiplexConnection<Http2ServerStream> implements Http2ServerConnection, HttpServerConnection {

  private final CompressionManager compressionManager;
  private final HttpServerMetrics<?, ?> httpMetrics;
  private final TransportMetrics<?> transportMetrics;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final Handler<HttpServerConnection> connectionHandler;
  private Handler<HttpServerStream> streamHandler;

  public Http2MultiplexServerConnection(Http2MultiplexHandler handler,
                                        CompressionManager compressionManager,
                                        HttpServerMetrics<?, ?> httpMetrics,
                                        TransportMetrics<?> transportMetrics,
                                        ChannelHandlerContext chctx,
                                        ContextInternal context,
                                        Supplier<ContextInternal> streamContextSupplier,
                                        Handler<HttpServerConnection> connectionHandler) {
    super(handler, transportMetrics, chctx, context);

    this.httpMetrics = httpMetrics;
    this.transportMetrics = transportMetrics;
    this.compressionManager = compressionManager;
    this.streamContextSupplier = streamContextSupplier;
    this.connectionHandler = connectionHandler;
  }

  @Override
  public Headers<CharSequence, CharSequence, ?> newHeaders() {
    return new DefaultHttp2Headers();
  }

  @Override
  public Http2ServerConnection streamHandler(Handler<HttpServerStream> handler) {
    this.streamHandler = handler;
    return this;
  }

  @Override
  boolean isServer() {
    return true;
  }

  // SHOULD UNIFY
  void receiveHeaders(ChannelHandlerContext chctx, Http2FrameStream frameStream, Http2Headers headers, boolean ended) {
    int streamId = frameStream.id();
    Http2ServerStream channel = stream(streamId);
    HttpRequestHeaders headersMap = new HttpRequestHeaders(headers);
    if (channel == null) {
      if (!headersMap.validate()) {
        chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR.code()));
      } else {
        Handler<HttpServerStream> handler = streamHandler;
        if (handler == null) {
          chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.REFUSED_STREAM.code()));
        } else {
          Http2ServerStream stream = Http2ServerStream.create(this, httpMetrics, transportMetrics, metric(),
                  streamContextSupplier.get(), null);;
          stream.init(streamId, chctx.channel().isWritable());
          registerChannel(stream, frameStream, chctx);
          ContextInternal streamContext = stream.context();
          streamContext.execute(stream.unwrap(), handler);
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
  public void writeHeaders(int streamId, Headers<CharSequence, CharSequence, ?> headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    Http2Headers prepare = (Http2Headers)headers;
    if (prepare.status() != null && compressionManager != null) {
      Http2ServerStream stream = stream(streamId);
      compressionManager.setContentEncoding(stream.headers().unwrap(), prepare);
    }
    writeStreamFrame(streamId, new DefaultHttp2HeadersFrame((Http2Headers) prepare, end), promise);
  }


  @Override
  public void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<Http2ServerStream> promise) {
    promise.fail("Push not supported");
  }
}
