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
package io.vertx.core.http.impl.tcp;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Handler;
import io.vertx.core.ThreadingModel;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.CompressionManager;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http1.Http1ServerConnection;
import io.vertx.core.http.impl.http1.HttpChunkContentCompressor;
import io.vertx.core.http.impl.http1.VertxHttpRequestDecoder;
import io.vertx.core.http.impl.http1.VertxHttpResponseEncoder;
import io.vertx.core.http.impl.http2.Http2ServerChannelInitializer;
import io.vertx.core.http.impl.http2.codec.Http2CodecServerChannelInitializer;
import io.vertx.core.http.impl.http2.multiplex.Http2MultiplexServerChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.impl.*;
import io.vertx.core.net.impl.tcp.NetServerImpl;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.tracing.TracingPolicy;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * A channel initializer that takes care of configuring a blank channel for HTTP to Vert.x {@link io.vertx.core.http.HttpServerRequest}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerConnectionInitializer {

  private final ContextInternal context;
  private final ThreadingModel threadingModel;
  private final boolean strictThreadMode;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final TcpHttpServer server;
  private final String serverOrigin;
  private final boolean disableH2C;
  private final Handler<HttpServerConnection> connectionHandler;
  private final Handler<Throwable> exceptionHandler;
  private final HttpServerMetrics<?, ?> httpMetrics;
  private final TransportMetrics<?> transportMetrics;
  private final Object metric;
  private final boolean useCompression;
  private final boolean useDecompression;
  private final boolean handle100ContinueAutomatically;
  private final int maxFormAttributeSize;
  private final int maxFormFields;
  private final int maxFormBufferedBytes;
  private final Http1ServerConfig http1Config;
  private final Http2ServerConfig http2Config;
  private final boolean registerWebSocketWriteHandlers;
  private final WebSocketServerConfig webSocketConfig;
  private final CompressionManager compressionManager;
  private final ServerSSLOptions sslOptions;
  private final int compressionContentSizeThreshold;
  private final Http2ServerChannelInitializer http2ChannelInitializer;
  private final TracingPolicy tracingPolicy;

  public HttpServerConnectionInitializer(ContextInternal context,
                                  ThreadingModel threadingModel,
                                  boolean strictThreadMode,
                                  Supplier<ContextInternal> streamContextSupplier,
                                  TcpHttpServer server,
                                  boolean useCompression,
                                  boolean useDecompression,
                                  TracingPolicy tracingPolicy,
                                  boolean logEnabled,
                                  CompressionOptions[] compressionOptions,
                                  int compressionContentSizeThreshold,
                                  boolean handle100ContinueAutomatically,
                                  int maxFormAttributeSize,
                                  int maxFormFields,
                                  int maxFormBufferedBytes,
                                  Http1ServerConfig http1Config,
                                  Http2ServerConfig http2Config,
                                  boolean registerWebSocketWriteHandlers,
                                  WebSocketServerConfig webSocketConfig,
                                  ServerSSLOptions sslOptions,
                                  String serverOrigin,
                                  Handler<HttpServerConnection> connectionHandler,
                                  Handler<Throwable> exceptionHandler,
                                  HttpServerMetrics<?, ?> httpMetrics,
                                  TransportMetrics<?> transportMetrics,
                                  Object metric) {

    CompressionManager compressionManager;
    if (useCompression) {
      compressionManager = new CompressionManager(compressionContentSizeThreshold, compressionOptions);
    } else {
      compressionManager = null;
    }

    Http2ServerChannelInitializer http2ChannelInitalizer;
    if (http2Config.getMultiplexImplementation()) {
      http2ChannelInitalizer = new Http2MultiplexServerChannelInitializer(
        context,
        compressionManager,
        useDecompression,
        server.getMetrics(),
        transportMetrics,
        metric,
        streamContextSupplier,
        connectionHandler,
        HttpUtils.fromVertxInitialSettings(true, http2Config.getInitialSettings()),
        http2Config.getRstFloodMaxRstFramePerWindow(),
        (int)http2Config.getRstFloodWindowDuration().toSeconds(),
        logEnabled);
    } else {
      http2ChannelInitalizer = new Http2CodecServerChannelInitializer(
        this,
        tracingPolicy,
        httpMetrics,
        transportMetrics,
        useDecompression,
        useCompression,
        http2Config,
        compressionManager,
        streamContextSupplier,
        connectionHandler,
        metric,
        logEnabled
      );
    }

    this.context = context;
    this.threadingModel = threadingModel;
    this.streamContextSupplier = streamContextSupplier;
    this.server = server;
    this.useCompression = useCompression;
    this.useDecompression = useDecompression;
    this.serverOrigin = serverOrigin;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    this.http1Config = http1Config;
    this.http2Config = http2Config;
    this.disableH2C = !http2Config.isClearTextEnabled();
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
    this.metric = metric;
    this.compressionManager = compressionManager;
    this.strictThreadMode = strictThreadMode;
    this.sslOptions = sslOptions;
    this.registerWebSocketWriteHandlers = registerWebSocketWriteHandlers;
    this.webSocketConfig = webSocketConfig;
    this.tracingPolicy = tracingPolicy;
    this.compressionContentSizeThreshold = compressionContentSizeThreshold;
    this.httpMetrics = httpMetrics;
    this.transportMetrics = transportMetrics;
    this.http2ChannelInitializer = http2ChannelInitalizer;
  }

  public void configurePipeline(Channel ch, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager, TransportMetrics<?> transportMetrics) {
    ChannelPipeline pipeline = ch.pipeline();
    if (sslOptions != null) {
      SslHandler sslHandler = pipeline.get(SslHandler.class);
      if (sslOptions.isUseAlpn()) {
        String protocol = sslHandler.applicationProtocol();
        if (protocol != null) {
          switch (protocol) {
            case "h2":
              configureHttp2(ch.pipeline(), true);
              break;
            case "http/1.1":
            case "http/1.0":
              configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
              configureHttp1Handler(ch.pipeline(), sslContextManager);
              break;
          }
        } else {
          // No alpn presented or OpenSSL
          configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
          configureHttp1Handler(ch.pipeline(), sslContextManager);
        }
      } else {
        configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
        configureHttp1Handler(ch.pipeline(), sslContextManager);
      }
    } else {
      if (disableH2C) {
        configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
        configureHttp1Handler(ch.pipeline(), sslContextManager);
      } else {
        Http1xOrH2CHandler handler = new Http1xOrH2CHandler() {
          @Override
          protected void configure(ChannelHandlerContext ctx, boolean h2c) {
            if (h2c) {
              configureHttp2(ctx.pipeline(), false);
            } else {
              configureHttp1Pipeline(ctx.pipeline(), sslChannelProvider, sslContextManager);
              http2ChannelInitializer.configureHttp1OrH2CUpgradeHandler(context, ctx.pipeline(), sslChannelProvider, sslContextManager);
            }
          }
          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            handleException(cause);
          }
        };
        // Handler that detects whether the HTTP/2 connection preface or just process the request
        // with the HTTP 1.x pipeline to support H2C with prior knowledge, i.e a client that connects
        // and uses HTTP/2 in clear text directly without an HTTP upgrade.
        String name = computeChannelName(pipeline);
        pipeline.addBefore(name, null, handler);
      }
    }
  }

  private void handleException(Throwable cause) {
    context.emit(cause, exceptionHandler);
  }

  private void sendServiceUnavailable(Channel ch) {
    ch.writeAndFlush(
      Unpooled.copiedBuffer("HTTP/1.1 503 Service Unavailable\r\n" +
        "Content-Length:0\r\n" +
        "\r\n", StandardCharsets.ISO_8859_1))
      .addListener(ChannelFutureListener.CLOSE);
  }

  private void configureHttp2(ChannelPipeline pipeline, boolean ssl) {
    http2ChannelInitializer.configureHttp2(context, pipeline, ssl);
    checkAccept(pipeline);
  }

  public void checkAccept(ChannelPipeline pipeline) {
    if (!server.requestAccept()) {
      // That should send an HTTP/2 go away
      pipeline.channel().close();
      return;
    }
  }

  /**
   * Compute the name of the logical end of pipeline when adding handlers to a preconfigured NetSocket pipeline.
   * See {@link NetServerImpl}
   * @param pipeline the channel pipeline
   * @return the name of the handler to use
   */
  private static String computeChannelName(ChannelPipeline pipeline) {
    if (pipeline.get(ChunkedWriteHandler.class) != null) {
      return "chunkedWriter";
    } else if (pipeline.get(IdleStateHandler.class) != null) {
      return "idle";
    } else {
      return "handler";
    }
  }

  private void configureHttp1Pipeline(ChannelPipeline pipeline, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    String name = computeChannelName(pipeline);
    pipeline.addBefore(name, "httpDecoder", new VertxHttpRequestDecoder(http1Config));
    pipeline.addBefore(name, "httpEncoder", new VertxHttpResponseEncoder());
    if (useDecompression) {
      pipeline.addBefore(name, "inflater", new HttpContentDecompressor(false));
    }
    if (useCompression) {
      pipeline.addBefore(name, "deflater", new HttpChunkContentCompressor(compressionContentSizeThreshold, compressionManager.options()));
    }
  }

  public void configureHttp1Handler(ChannelPipeline pipeline, SslContextManager sslContextManager) {
    if (!server.requestAccept()) {
      sendServiceUnavailable(pipeline.channel());
      return;
    }
    VertxHandler<Http1ServerConnection> handler = VertxHandler.create(chctx -> {
      Http1ServerConnection conn = new Http1ServerConnection(
        threadingModel,
        streamContextSupplier,
        strictThreadMode,
        handle100ContinueAutomatically,
        sslOptions,
        sslContextManager,
        maxFormAttributeSize,
        maxFormFields,
        maxFormBufferedBytes,
        http1Config,
        registerWebSocketWriteHandlers,
        webSocketConfig,
        chctx,
        context,
        serverOrigin,
        tracingPolicy,
        httpMetrics,
        transportMetrics);
      conn.metric(metric);
      return conn;
    });
    pipeline.replace(VertxHandler.class, "handler", handler);
    Http1ServerConnection conn = handler.getConnection();
    connectionHandler.handle(conn);
  }
}
