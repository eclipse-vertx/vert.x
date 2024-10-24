/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.vertx.core.http.HttpSettings;

import java.util.function.Function;

/**
 * Todo : don't use the parent builder that is too complicated and restrictive
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ConnectionHandlerBuilder<C extends Http2ConnectionBase> extends AbstractHttp2ConnectionHandlerBuilder<VertxHttp2ConnectionHandler<C>, VertxHttp2ConnectionHandlerBuilder<C>> {

  private boolean useDecompression;
  private CompressionOptions[] compressionOptions;
  private Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory;
  private boolean logEnabled;
  private boolean server;

  protected VertxHttp2ConnectionHandlerBuilder<C> server(boolean isServer) {
    this.server = isServer;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> initialSettings(io.vertx.core.http.Http2Settings settings) {
    HttpUtils.fromVertxInitialSettings(server, settings, initialSettings());
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> useCompression(CompressionOptions[] compressionOptions) {
    this.compressionOptions = compressionOptions;
    return this;
  }

  @Override
  protected VertxHttp2ConnectionHandlerBuilder<C> decoderEnforceMaxRstFramesPerWindow(int maxRstFramesPerWindow, int secondsPerWindow) {
    return super.decoderEnforceMaxRstFramesPerWindow(maxRstFramesPerWindow, secondsPerWindow);
  }

  @Override
  protected VertxHttp2ConnectionHandlerBuilder<C> gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
    return super.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
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
    if (logEnabled) {
      frameLogger(new Http2FrameLogger(LogLevel.DEBUG));
    }
    configureStreamByteDistributor();
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

  private void configureStreamByteDistributor() {
    DefaultHttp2Connection conn = new DefaultHttp2Connection(server, maxReservedStreams());
    StreamByteDistributor distributor = new UniformStreamByteDistributor(conn);
    conn.remote().flowController(new DefaultHttp2RemoteFlowController(conn, distributor));
    connection(conn);
  }

  @Override
  protected VertxHttp2ConnectionHandler<C> build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
    if (server) {
      if (compressionOptions != null) {
        encoder = new VertxCompressorHttp2ConnectionEncoder(encoder, compressionOptions);
      }
      VertxHttp2ConnectionHandler<C> handler = new VertxHttp2ConnectionHandler<>(connectionFactory, useDecompression,
        decoder, encoder, new HttpSettings(initialSettings));
      decoder.frameListener(handler);
      return handler;
    } else {
      VertxHttp2ConnectionHandler<C> handler = new VertxHttp2ConnectionHandler<>(connectionFactory, useDecompression,
        decoder, encoder, new HttpSettings(initialSettings));
      decoder.frameListener(handler);
      return handler;
    }
  }
}
