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
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.http2.Http2ClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientStream;
import io.vertx.core.http.impl.http2.Http2ClientStreamImpl;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2StreamBase;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;

public class Http2MultiplexClientConnection extends Http2MultiplexConnection implements HttpClientConnection, Http2ClientConnection {

  private final HostAndPort authority;
  private Promise<HttpClientConnection> completion;
  private long concurrency;

  public Http2MultiplexClientConnection(Http2MultiplexHandler handler,
                                        ChannelHandlerContext chctx,
                                        ContextInternal context,
                                        HostAndPort authority,
                                        Promise<HttpClientConnection> completion) {
    super(handler, chctx, context);

    this.authority = authority;
    this.completion = completion;
  }

  void registerStream(ChannelHandlerContext chctx, Http2FrameStream str, Http2ClientStream blah) {
    channels.put(blah.id, new StreamChannel(blah, str, chctx));
  }

  @Override
  void onInitialSettings(Http2Settings settings) {
    concurrency = settings.getMaxConcurrentStreams();
    Promise<HttpClientConnection> c = completion;
    completion = null;
    c.complete(this);
  }

  @Override
  void onSettings(Http2Settings settings) {
    concurrency = settings.getMaxConcurrentStreams();
  }

  @Override
  boolean isServer() {
    return false;
  }

  @Override
  void receiveHeaders(ChannelHandlerContext chctx, Http2FrameStream frameStream, Http2Headers headers, boolean ended) {
    int streamId = frameStream.id();
    StreamChannel channel = channels.get(streamId);
    Http2StreamBase stream = channel.stream;
    Http2HeadersMultiMap headersMap = new Http2HeadersMultiMap(headers);
    if (!stream.isHeadersReceived()) {
      if (!headersMap.validate(false)) {
        chctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR.code()));
      } else {
        headersMap.sanitize();
        stream.onHeaders(headersMap);
        if (ended) {
          stream.onTrailers();
        }
      }
    } else {
      stream.onTrailers(headersMap);
    }
  }

  @Override
  public long activeStreams() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long concurrency() {
    return concurrency;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return this;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    return this;
  }

  @Override
  public boolean pooled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    Http2ClientStreamImpl stream = new Http2ClientStreamImpl(this, context, null, false, null, false);
    return context.succeededFuture(stream);
  }

  @Override
  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> createStream(Http2ClientStream vertxStream) {
    return handler.connect(vertxStream);
  }

  @Override
  public void writeHeaders(int streamId, Http2HeadersMultiMap headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    writeStreamFrame(streamId, new DefaultHttp2HeadersFrame((Http2Headers) headers.prepare().unwrap(), end), promise);
    StreamChannel ch = channels.remove(streamId);
    int newId = ch.frameStream.id();
    channels.put(newId, ch);
    ch.stream.id = newId;
  }

  @Override
  public void writePriorityFrame(int streamId, StreamPriority priority) {
    throw new UnsupportedOperationException();
  }

}
