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
package io.vertx.core.http.impl.http2.codec;

import io.netty.channel.ChannelPipeline;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerConnectionInitializer;
import io.vertx.core.http.impl.http2.Http2ServerChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.function.Supplier;

public class Http2CodecServerChannelInitializer implements Http2ServerChannelInitializer {

  private final HttpServerConnectionInitializer initializer;
  private final HttpServerMetrics serverMetrics;
  private final Object metric;
  private final HttpServerOptions options;
  private final CompressionManager compressionManager;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final Handler<HttpServerConnection> connectionHandler;
  private final String serverOrigin;
  private final boolean logEnabled;

  public Http2CodecServerChannelInitializer(HttpServerConnectionInitializer initializer,
                                            HttpServerMetrics serverMetrics,
                                            HttpServerOptions options,
                                            CompressionManager compressionManager,
                                            Supplier<ContextInternal> streamContextSupplier,
                                            Handler<HttpServerConnection> connectionHandler,
                                            String serverOrigin,
                                            Object metric,
                                            boolean logEnabled) {
    this.initializer = initializer;
    this.serverMetrics = serverMetrics;
    this.options = options;
    this.compressionManager = compressionManager;
    this.streamContextSupplier = streamContextSupplier;
    this.connectionHandler = connectionHandler;
    this.serverOrigin = serverOrigin;
    this.metric = metric;
    this.logEnabled = logEnabled;
  }

  @Override
  public void configureHttp2(ContextInternal context, ChannelPipeline pipeline, boolean ssl) {
    VertxHttp2ConnectionHandler<Http2ServerConnectionImpl> handler = buildHttp2ConnectionHandler(context);
    pipeline.replace(VertxHandler.class, "handler", handler);
  }
  @Override
  public void configureHttp1OrH2CUpgradeHandler(ContextInternal context, ChannelPipeline pipeline, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    pipeline.addAfter("httpEncoder", "h2c", new Http1xUpgradeToH2CHandler(initializer, context, this, sslChannelProvider, sslContextManager, options.isCompressionSupported(), options.isDecompressionSupported()));
  }

  public VertxHttp2ConnectionHandler<Http2ServerConnectionImpl> buildHttp2ConnectionHandler(ContextInternal ctx) {
    int maxRstFramesPerWindow = options.getHttp2RstFloodMaxRstFramePerWindow();
    int secondsPerWindow = (int)options.getHttp2RstFloodWindowDurationTimeUnit().toSeconds(options.getHttp2RstFloodWindowDuration());
    VertxHttp2ConnectionHandler<Http2ServerConnectionImpl> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnectionImpl>()
      .server(true)
      .useCompression(compressionManager != null ? compressionManager.options() : null)
      .gracefulShutdownTimeoutMillis(0)
      .decoderEnforceMaxRstFramesPerWindow(maxRstFramesPerWindow, secondsPerWindow)
      .useDecompression(options.isDecompressionSupported())
      .initialSettings(options.getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ServerConnectionImpl conn = new Http2ServerConnectionImpl(ctx, streamContextSupplier, connHandler, compressionManager != null ? compressionManager::determineEncoding : null, options, serverMetrics);
        conn.metric(metric);
        return conn;
      })
      .logEnabled(logEnabled)
      .build();
    handler.addHandler(connectionHandler::handle);
    return handler;
  }
}
