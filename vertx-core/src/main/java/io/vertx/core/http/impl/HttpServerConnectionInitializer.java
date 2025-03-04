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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A channel initializer that takes care of configuring a blank channel for HTTP to Vert.x {@link io.vertx.core.http.HttpServerRequest}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpServerConnectionInitializer {

  private final ContextInternal context;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final VertxInternal vertx;
  private final HttpServerImpl server;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final boolean logEnabled;
  private final boolean disableH2C;
  private final Handler<HttpServerConnection> connectionHandler;
  private final Handler<Throwable> exceptionHandler;
  private final Object metric;
  private final CompressionOptions[] compressionOptions;
  private final int compressionContentSizeThreshold;
  private final Function<String, String> encodingDetector;

  HttpServerConnectionInitializer(ContextInternal context,
                                  Supplier<ContextInternal> streamContextSupplier,
                                  HttpServerImpl server,
                                  VertxInternal vertx,
                                  HttpServerOptions options,
                                  String serverOrigin,
                                  Handler<HttpServerConnection> connectionHandler,
                                  Handler<Throwable> exceptionHandler,
                                  Object metric) {

    CompressionOptions[] compressionOptions = null;
    if (options.isCompressionSupported()) {
      List<CompressionOptions> compressors = options.getCompressors();
      if (compressors == null) {
        int compressionLevel = options.getCompressionLevel();
        compressionOptions = new CompressionOptions[] { StandardCompressionOptions.gzip(compressionLevel, 15, 8), StandardCompressionOptions.deflate(compressionLevel, 15, 8) };
      } else {
        compressionOptions = compressors.toArray(new CompressionOptions[0]);
      }
    }

    this.context = context;
    this.streamContextSupplier = streamContextSupplier;
    this.server = server;
    this.vertx = vertx;
    this.options = options;
    this.serverOrigin = serverOrigin;
    this.logEnabled = options.getLogActivity();
    this.disableH2C = !options.isHttp2ClearTextEnabled();
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
    this.metric = metric;
    this.compressionOptions = compressionOptions;
    this.compressionContentSizeThreshold = options.getCompressionContentSizeThreshold();
    this.encodingDetector = compressionOptions != null ? new EncodingDetector(options.getCompressionContentSizeThreshold(), compressionOptions)::determineEncoding : null;
  }

  void configurePipeline(Channel ch, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    ChannelPipeline pipeline = ch.pipeline();
    if (options.isSsl()) {
      if (options.isUseAlpn()) {
        String protocol;
        if (options.isHttp3()) {
          protocol = Objects.requireNonNull(((QuicChannel) ch).sslEngine()).getApplicationProtocol();
        } else {
          protocol = pipeline.get(SslHandler.class).applicationProtocol();
        }

        if (protocol != null) {
          switch (protocol) {
            case "h3":
              configureHttp3(ch.pipeline());
              break;
            case "h2":
              configureHttp2(ch.pipeline());
              break;
            case "http/1.1":
            case "http/1.0":
              configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
              configureHttp1Handler(ch.pipeline(), sslChannelProvider, sslContextManager);
              break;
          }
        } else {
          // No alpn presented or OpenSSL
          configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
          configureHttp1Handler(ch.pipeline(), sslChannelProvider, sslContextManager);
        }
      } else {
        configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
        configureHttp1Handler(ch.pipeline(), sslChannelProvider, sslContextManager);
      }
    } else {
      if (disableH2C) {
        configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
        configureHttp1Handler(ch.pipeline(), sslChannelProvider, sslContextManager);
      } else {
        Http1xOrH2CHandler handler = new Http1xOrH2CHandler() {
          @Override
          protected void configure(ChannelHandlerContext ctx, boolean h2c) {
            if (h2c) {
              configureHttp2(ctx.pipeline());
            } else {
              configureHttp1Pipeline(ctx.pipeline(), sslChannelProvider, sslContextManager);
              configureHttp1OrH2CUpgradeHandler(ctx.pipeline(), sslChannelProvider, sslContextManager);
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

  private void configureHttp3(ChannelPipeline pipeline) {
    configureHttp3Handler(pipeline);
    configureHttp3Pipeline(pipeline);
  }

  private void configureHttp3Handler(ChannelPipeline pipeline) {
    VertxHttp3ConnectionHandler<Http3ServerConnection> handler = buildHttp3ConnectionHandler(context,
      connectionHandler);
    pipeline.replace("handler", "handler", handler);
    pipeline.addLast(handler.getHttp3ConnectionHandler());
  }

  void configureHttp3Pipeline(ChannelPipeline pipeline) {
    if (!server.requestAccept()) {
      // That should send an HTTP/3 go away
      pipeline.channel().close();
      return;
    }
  }

  private VertxHttp3ConnectionHandler<Http3ServerConnection> buildHttp3ConnectionHandler(ContextInternal ctx,
                                                                                         Handler<HttpServerConnection> handler_) {
    //TODO: set correct props for VertxHttp3ConnectionHandlerBuilder:
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
//    int maxRstFramesPerWindow = options.getHttp2RstFloodMaxRstFramePerWindow();
//    int secondsPerWindow = (int)options.getHttp2RstFloodWindowDurationTimeUnit().toSeconds(options.getHttp2RstFloodWindowDuration());
    VertxHttp3ConnectionHandler<Http3ServerConnection> handler =
      new VertxHttp3ConnectionHandlerBuilder<Http3ServerConnection>()
      .server(true)
//      .useCompression(compressionOptions)
//      .gracefulShutdownTimeoutMillis(0)
//      .decoderEnforceMaxRstFramesPerWindow(maxRstFramesPerWindow, secondsPerWindow)
//      .useDecompression(options.isDecompressionSupported())
      .httpSettings(HttpUtils.fromVertxSettings(options.getInitialHttp3Settings()))
      .connectionFactory(connHandler -> {
        Http3ServerConnection conn = new Http3ServerConnection(ctx, streamContextSupplier, serverOrigin, connHandler,
          encodingDetector, options, metrics);
        conn.metric(metric);
        return conn;
      })
        //TODO: set log enable
//      .logEnabled(logEnabled)
      .build(ctx);
    handler.addHandler(conn -> {
      //TODO: set correct props for VertxHttp3ConnectionHandlerBuilder:
//      if (options.getHttp2ConnectionWindowSize() > 0) {
//        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
//      }
      handler_.handle(conn);
    });
    return handler;
  }

  private void configureHttp2(ChannelPipeline pipeline) {
    configureHttp2Handler(pipeline);
    configureHttp2Pipeline(pipeline);
  }

  private void configureHttp2Handler(ChannelPipeline pipeline) {
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = buildHttp2ConnectionHandler(context, connectionHandler);
    pipeline.replace(VertxHandler.class, "handler", handler);
  }

  void configureHttp2Pipeline(ChannelPipeline pipeline) {
    if (!server.requestAccept()) {
      // That should send an HTTP/2 go away
      pipeline.channel().close();
      return;
    }
  }

  VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler() {
    return buildHttp2ConnectionHandler(context, connectionHandler);
  }

  private VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler(ContextInternal ctx, Handler<HttpServerConnection> handler_) {
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
    int maxRstFramesPerWindow = options.getHttp2RstFloodMaxRstFramePerWindow();
    int secondsPerWindow = (int)options.getHttp2RstFloodWindowDurationTimeUnit().toSeconds(options.getHttp2RstFloodWindowDuration());
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnection>()
      .server(true)
      .useCompression(compressionOptions)
      .gracefulShutdownTimeoutMillis(0)
      .decoderEnforceMaxRstFramesPerWindow(maxRstFramesPerWindow, secondsPerWindow)
      .useDecompression(options.isDecompressionSupported())
      .initialSettings(options.getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ServerConnection conn = new Http2ServerConnection(ctx, streamContextSupplier, serverOrigin, connHandler, encodingDetector, options, metrics);
        conn.metric(metric);
        return conn;
      })
      .logEnabled(logEnabled)
      .build();
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      handler_.handle(conn);
    });
    return handler;
  }

  private void configureHttp1OrH2CUpgradeHandler(ChannelPipeline pipeline, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    // DO WE NEED TO ADD SOMEWHERE BEFORE IDLE ?
    pipeline.addAfter("httpEncoder", "h2c", new Http1xUpgradeToH2CHandler(this, sslChannelProvider, sslContextManager, options.isCompressionSupported(), options.isDecompressionSupported()));
  }

  /**
   * Compute the name of the logical end of pipeline when adding handlers to a preconfigured NetSocket pipeline.
   * See {@link io.vertx.core.net.impl.NetServerImpl}
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
    pipeline.addBefore(name, "httpDecoder", new VertxHttpRequestDecoder(options));
    pipeline.addBefore(name, "httpEncoder", new VertxHttpResponseEncoder());
    if (options.isDecompressionSupported()) {
      pipeline.addBefore(name, "inflater", new HttpContentDecompressor(false));
    }
    if (options.isCompressionSupported()) {
      pipeline.addBefore(name, "deflater", new HttpChunkContentCompressor(compressionContentSizeThreshold, compressionOptions));
    }
  }

  void configureHttp1Handler(ChannelPipeline pipeline, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    if (!server.requestAccept()) {
      sendServiceUnavailable(pipeline.channel());
      return;
    }
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(
        streamContextSupplier,
        sslContextManager,
        options,
        chctx,
        context,
        serverOrigin,
        metrics);
      conn.metric(metric);
      return conn;
    });
    pipeline.replace(VertxHandler.class, "handler", handler);
    Http1xServerConnection conn = handler.getConnection();
    connectionHandler.handle(conn);
  }

  private static class EncodingDetector extends HttpContentCompressor {

    private EncodingDetector(int contentSizeThreshold, CompressionOptions[] compressionOptions) {
      super(contentSizeThreshold, compressionOptions);
    }

    @Override
    protected String determineEncoding(String acceptEncoding) {
      return super.determineEncoding(acceptEncoding);
    }
  }
}
