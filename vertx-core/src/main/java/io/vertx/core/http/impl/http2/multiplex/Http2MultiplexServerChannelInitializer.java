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

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
import io.vertx.core.Handler;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.http2.Http2ServerChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.function.Supplier;

public class Http2MultiplexServerChannelInitializer implements Http2ServerChannelInitializer {

  private final CompressionManager compressionManager;
  private final boolean decompressionSupported;
  private final Http2Settings initialSettings;
  private final Http2MultiplexConnectionFactory connectionFactory;
  private final boolean logEnabled;

  public Http2MultiplexServerChannelInitializer(ContextInternal context,
                                                CompressionManager compressionManager,
                                                boolean decompressionSupported,
                                                HttpServerMetrics<?, ?, ?> serverMetrics,
                                                Object connectionMetric,
                                                Supplier<ContextInternal> streamContextSupplier,
                                                Handler<HttpServerConnection> connectionHandler,
                                                Http2Settings initialSettings,
                                                boolean logEnabled) {
    Http2MultiplexConnectionFactory connectionFactory = (handler, chctx) -> {
      Http2MultiplexServerConnection connection = new Http2MultiplexServerConnection(
        handler,
        compressionManager,
        serverMetrics,
        chctx,
        context,
        streamContextSupplier,
        connectionHandler);
      connection.metric(connectionMetric);
      return connection;
    };
    this.initialSettings = initialSettings;
    this.connectionFactory = connectionFactory;
    this.compressionManager = compressionManager;
    this.decompressionSupported = decompressionSupported;
    this.logEnabled = logEnabled;
  }

  @Override
  public void configureHttp2(ContextInternal context, ChannelPipeline pipeline, boolean ssl) {
    Http2MultiplexHandler handler = new Http2MultiplexHandler(
      pipeline.channel(),
      context,
      connectionFactory,
      initialSettings);

    Http2FrameCodec frameCodec = new Http2CustomFrameCodecBuilder(compressionManager, decompressionSupported)
      .server(true)
      .initialSettings(initialSettings)
      .logEnabled(logEnabled)
      .build();
    frameCodec.connection().addListener(handler);

    if (ssl) {
      pipeline.remove("chunkedWriter");
    }
    pipeline.remove("handler");
    pipeline.addLast("codec", frameCodec);
    pipeline.addLast("multiplex", new io.netty.handler.codec.http2.Http2MultiplexHandler(handler));
    pipeline.addLast("handler", handler);
  }
  @Override
  public void configureHttp1OrH2CUpgradeHandler(ContextInternal context, ChannelPipeline pipeline, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
      if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
        Http2MultiplexHandler handler = new Http2MultiplexHandler(
          pipeline.channel(),
          context,
          connectionFactory,
          initialSettings);
        Http2FrameCodec frameCodec = new Http2CustomFrameCodecBuilder(compressionManager, decompressionSupported)
          .server(true)
          .initialSettings(initialSettings)
          .logEnabled(logEnabled)
          .build();
        frameCodec.connection().addListener(handler);
        io.netty.handler.codec.http2.Http2MultiplexHandler http2MultiplexHandler = new io.netty.handler.codec.http2.Http2MultiplexHandler(handler);
        return new Http2ServerUpgradeCodec(
          frameCodec,
          http2MultiplexHandler,
          handler);
      } else {
        return null;
      }
    };
    HttpServerUpgradeHandler.SourceCodec sourceCodec = ctx -> {
      ChannelPipeline p = ctx.pipeline();
      if (p.get("chunkedWriter") != null) {
        p.remove("chunkedWriter");
      }
      p.remove("httpDecoder");
      p.remove("httpEncoder");
      p.remove("handler");
      if (decompressionSupported) {
        p.remove("inflater");
      }
      if (compressionManager != null) {
        p.remove("deflater");
      }
    };
    // Make max buffered configurable
    pipeline.addBefore("handler", "h2c", new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory, 128 * 1024));
  }
}
