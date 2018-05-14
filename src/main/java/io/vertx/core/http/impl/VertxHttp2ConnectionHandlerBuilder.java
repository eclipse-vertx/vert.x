/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.CompressorHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.vertx.core.http.HttpServerOptions;

import java.util.function.Function;

/**
 * Todo : don't use the parent builder that is too complicated and restrictive
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ConnectionHandlerBuilder<C extends Http2ConnectionBase> extends AbstractHttp2ConnectionHandlerBuilder<VertxHttp2ConnectionHandler<C>, VertxHttp2ConnectionHandlerBuilder<C>> {

  private boolean useCompression;
  private boolean useDecompression;
  private int compressionLevel = HttpServerOptions.DEFAULT_COMPRESSION_LEVEL;
  private io.vertx.core.http.Http2Settings initialSettings;
  private Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory;
  private boolean logEnabled;

  protected VertxHttp2ConnectionHandlerBuilder<C> server(boolean isServer) {
    return super.server(isServer);
  }

  VertxHttp2ConnectionHandlerBuilder<C> initialSettings(io.vertx.core.http.Http2Settings settings) {
    this.initialSettings = settings;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> useCompression(boolean useCompression) {
    this.useCompression = useCompression;
    return this;
  }

  /**
   * This method allows to set the compression level to be used in the http/2 connection encoder
   * (for data sent to client) when compression support is turned on (@see useCompression) and
   * the client advertises to support deflate/gizip compression in the Accept-Encoding header
   *
   * default value is : 6 (Netty legacy)
   *
   * While one can think that best value is always the maximum compression ratio,
   * there's a trade-off to consider: the most compressed level requires the most computatinal work to compress/decompress,
   * E.g. you have it set fairly high on a high-volume website, you may experience performance degradation
   * and latency on resource serving due to CPU overload, and however - as the comptational work is required also client side
   * while decompressing - setting an higher compression level can result in an overall higher page load time
   * especially nowadays when many clients are handled mobile devices with a low CPU profile.
   *
   * see also: http://www.gzip.org/algorithm.txt
   *
   * @param compressionLevel integer 1-9, 1 means use fastest algorithm, 9 slower algorithm but better compression ratio
   * @return a reference to this instance for fulent API coding style
   */
  VertxHttp2ConnectionHandlerBuilder<C> compressionLevel(int compressionLevel) {
    this.compressionLevel = compressionLevel;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> useDecompression(boolean useDecompression) {
    this.useDecompression = useDecompression;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> connectionFactory(Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory) {
    this.connectionFactory = connectionFactory;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> logEnabled(boolean logEnabled) {
    this.logEnabled = logEnabled;
    return this;
  }

  @Override
  protected VertxHttp2ConnectionHandler<C> build() {
    if (initialSettings != null) {
      HttpUtils.fromVertxInitialSettings(isServer(), initialSettings, initialSettings());
    }
    if (logEnabled) {
      frameLogger(new Http2FrameLogger(LogLevel.DEBUG));
    }
    // Make this damn builder happy
    frameListener(new Http2FrameListener() {
      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onPingRead(ChannelHandlerContext channelHandlerContext, long l) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onPingAckRead(ChannelHandlerContext channelHandlerContext, long l) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
      @Override
      public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) throws Http2Exception {
        throw new UnsupportedOperationException();
      }
    });
    return super.build();
  }

  @Override
  protected VertxHttp2ConnectionHandler<C> build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
    if (isServer()) {
      if (useCompression) {
        encoder = new CompressorHttp2ConnectionEncoder(encoder,compressionLevel,CompressorHttp2ConnectionEncoder.DEFAULT_WINDOW_BITS,CompressorHttp2ConnectionEncoder.DEFAULT_MEM_LEVEL);
      }
      VertxHttp2ConnectionHandler<C> handler = new VertxHttp2ConnectionHandler<>(connectionFactory, useDecompression, decoder, encoder, initialSettings);
      decoder.frameListener(handler);
      return handler;
    } else {
      VertxHttp2ConnectionHandler<C> handler = new VertxHttp2ConnectionHandler<>(connectionFactory, useCompression, decoder, encoder, initialSettings);
      decoder.frameListener(handler);
      return handler;
    }
  }
}
