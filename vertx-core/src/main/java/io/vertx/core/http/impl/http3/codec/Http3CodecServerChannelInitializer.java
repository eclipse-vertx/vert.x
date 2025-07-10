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
package io.vertx.core.http.impl.http3.codec;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerConnectionInitializer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http3.Http3ServerChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.function.Supplier;

public class Http3CodecServerChannelInitializer implements Http3ServerChannelInitializer {

  private final HttpServerMetrics serverMetrics;
  private final Object metric;
  private final HttpServerOptions options;
  private final CompressionManager compressionManager;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final Handler<HttpServerConnection> connectionHandler;
  private final GlobalTrafficShapingHandler trafficShapingHandler;

  public Http3CodecServerChannelInitializer(HttpServerConnectionInitializer initializer,
                                            HttpServerMetrics serverMetrics,
                                            HttpServerOptions options,
                                            CompressionManager compressionManager,
                                            Supplier<ContextInternal> streamContextSupplier,
                                            Handler<HttpServerConnection> connectionHandler,
                                            String serverOrigin,
                                            Object metric,
                                            boolean logEnabled,
                                            GlobalTrafficShapingHandler trafficShapingHandler) {
    this.serverMetrics = serverMetrics;
    this.options = options;
    this.compressionManager = compressionManager;
    this.streamContextSupplier = streamContextSupplier;
    this.connectionHandler = connectionHandler;
    this.metric = metric;
    this.trafficShapingHandler = trafficShapingHandler;
  }

  public void configureHttp3(ContextInternal context, ChannelPipeline pipeline) {
    VertxHttp3ConnectionHandler<Http3ServerConnectionImpl> handler = buildHttp3ConnectionHandler(context);
    pipeline.replace("handler", "handler", handler);
    pipeline.addLast(handler.getHttp3ConnectionHandler());
  }


  private VertxHttp3ConnectionHandler<Http3ServerConnectionImpl> buildHttp3ConnectionHandler(ContextInternal ctx) {
    //TODO: set correct props for VertxHttp3ConnectionHandlerBuilder:
    VertxHttp3ConnectionHandler<Http3ServerConnectionImpl> handler =
      new VertxHttp3ConnectionHandlerBuilder<Http3ServerConnectionImpl>()
      .server(true)
      .trafficShapingHandler(trafficShapingHandler)
      .httpSettings(HttpUtils.fromVertxSettings(options.getInitialHttp3Settings()))
      .connectionFactory(connHandler -> {
        Http3ServerConnectionImpl conn = new Http3ServerConnectionImpl(ctx, streamContextSupplier, connHandler,
          compressionManager != null ? compressionManager::determineEncoding : null, options, serverMetrics);
        conn.metric(metric);
        return conn;
      })
      .build(ctx);
    handler.addHandler(connectionHandler::handle);
    return handler;
  }
}
