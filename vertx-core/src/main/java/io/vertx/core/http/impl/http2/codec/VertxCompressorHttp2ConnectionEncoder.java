/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http2.CompressorHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2LifecycleManager;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsReceivedConsumer;
import io.vertx.core.http.impl.http2.Http2ServerStream;

import java.util.function.Function;

import static io.vertx.core.http.HttpHeaders.CONTENT_ENCODING;
import static io.vertx.core.http.HttpHeaders.IDENTITY;

public class VertxCompressorHttp2ConnectionEncoder implements Http2FrameWriter, Http2ConnectionEncoder, Http2SettingsReceivedConsumer {

  private Http2ConnectionEncoder delegate;
  private final Http2ConnectionEncoder plainEncoder;

  public VertxCompressorHttp2ConnectionEncoder(Http2ConnectionEncoder plainEncoder, CompressionOptions[] compressionOptions) {
    this.delegate = new CompressorHttp2ConnectionEncoder(plainEncoder, compressionOptions);
    this.plainEncoder = plainEncoder;
  }

  private void beforeWritingHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers responseHeaders) {
    String contentEncodingToApply = determineContentEncodingToApply(ctx, streamId, responseHeaders);
    if (contentEncodingToApply == null || contentEncodingToApply.equalsIgnoreCase(IDENTITY.toString())) {
      if (responseHeaders.contains(CONTENT_ENCODING, IDENTITY)) {
        responseHeaders.remove(CONTENT_ENCODING);
      }
      delegate = plainEncoder;
    } else {
      responseHeaders.set(CONTENT_ENCODING, contentEncodingToApply);
    }
  }

  private String determineContentEncodingToApply(ChannelHandlerContext ctx, int streamId, Http2Headers responseHeaders) {
    if (responseHeaders.contains(CONTENT_ENCODING)) {
      return null;
    }
    return ifType(ctx.handler(), VertxHttp2ConnectionHandler.class, connectionHandler ->
      ifType(connectionHandler.connectFuture().getNow(), Http2ServerConnectionImpl.class, connection ->
        ifType(connection.stream(streamId), Http2ServerStream.class, stream ->
          stream.headers() == null ? null : connection.determineContentEncoding(stream.headers()))));
  }

  private <T, R> R ifType(Object obj, Class<T> type, Function<T, R> then) {
    return obj != null && type.isAssignableFrom(obj.getClass()) ? then.apply(type.cast(obj)) : null;
  }

  @Override
  public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
    beforeWritingHeaders(ctx, streamId, headers);
    return delegate.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
  }

  @Override
  public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream, ChannelPromise promise) {
    beforeWritingHeaders(ctx, streamId, headers);
    return delegate.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream, promise);
  }

  @Override
  public void lifecycleManager(Http2LifecycleManager http2LifecycleManager) {
    delegate.lifecycleManager(http2LifecycleManager);
  }

  @Override
  public Http2Connection connection() {
    return delegate.connection();
  }

  @Override
  public Http2RemoteFlowController flowController() {
    return delegate.flowController();
  }

  @Override
  public Http2FrameWriter frameWriter() {
    return delegate.frameWriter();
  }

  @Override
  public Http2Settings pollSentSettings() {
    return delegate.pollSentSettings();
  }

  @Override
  public void remoteSettings(Http2Settings http2Settings) throws Http2Exception {
    delegate.remoteSettings(http2Settings);
  }

  @Override
  public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
    return delegate.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
  }

  @Override
  public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode, ChannelPromise promise) {
    return delegate.writeRstStream(ctx, streamId, errorCode, promise);
  }

  @Override
  public ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings, ChannelPromise promise) {
    return delegate.writeSettings(ctx, settings, promise);
  }

  @Override
  public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
    return delegate.writeSettingsAck(ctx, promise);
  }

  @Override
  public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
    return delegate.writePing(ctx, ack, data, promise);
  }

  @Override
  public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding, ChannelPromise promise) {
    return delegate.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
  }

  @Override
  public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData, ChannelPromise promise) {
    return delegate.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
  }

  @Override
  public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement, ChannelPromise promise) {
    return delegate.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
  }

  @Override
  public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload, ChannelPromise promise) {
    return delegate.writeFrame(ctx, frameType, streamId, flags, payload, promise);
  }

  @Override
  public Configuration configuration() {
    return delegate.configuration();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream, ChannelPromise promise) {
    return delegate.writeData(ctx, streamId, data, padding, endStream, promise);
  }

  @Override
  public void consumeReceivedSettings(Http2Settings settings) {
    if (delegate instanceof Http2SettingsReceivedConsumer) {
      ((Http2SettingsReceivedConsumer) delegate).consumeReceivedSettings(settings);
    } else {
      throw new IllegalStateException("delegate " + delegate + " is not an instance of " +
        Http2SettingsReceivedConsumer.class);
    }
  }
}
