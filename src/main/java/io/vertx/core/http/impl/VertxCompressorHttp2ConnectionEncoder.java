/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http2.CompressorHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;

/**
 * A {@link CompressorHttp2ConnectionEncoder} that does not compress a response whose {@code content-encoding}
 * was set by the application: the body is already encoded and is written as is.
 */
class VertxCompressorHttp2ConnectionEncoder extends CompressorHttp2ConnectionEncoder {

  private final Http2ConnectionEncoder plainEncoder;

  VertxCompressorHttp2ConnectionEncoder(Http2ConnectionEncoder plainEncoder, CompressionOptions[] compressionOptions) {
    super(plainEncoder, compressionOptions);
    this.plainEncoder = plainEncoder;
  }

  @Override
  public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
    if (isEncodedByApplication(ctx, streamId)) {
      return plainEncoder.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }
    return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
  }

  @Override
  public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream, ChannelPromise promise) {
    if (isEncodedByApplication(ctx, streamId)) {
      return plainEncoder.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream, promise);
    }
    return super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream, promise);
  }

  private static boolean isEncodedByApplication(ChannelHandlerContext ctx, int streamId) {
    if (ctx.handler() instanceof VertxHttp2ConnectionHandler) {
      Object connection = ((VertxHttp2ConnectionHandler<?>) ctx.handler()).connectFuture().getNow();
      if (connection instanceof Http2ServerConnection) {
        VertxHttp2Stream<?> stream = ((Http2ServerConnection) connection).stream(streamId);
        if (stream instanceof Http2ServerStream) {
          Http2ServerStreamHandler request = ((Http2ServerStream) stream).request;
          return request != null && request.response().isEncodedByApplication();
        }
      }
    }
    return false;
  }
}
