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
import io.netty.channel.*;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A channel initializer that will takes care of configuring a blank channel for HTTP
 * to Vert.x {@link io.vertx.core.http.HttpServerRequest}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerWorker implements BiConsumer<Channel, SslChannelProvider> {

  final ContextInternal context;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final VertxInternal vertx;
  private final HttpServerImpl server;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final boolean logEnabled;
  private final boolean disableH2C;
  final Handler<HttpServerConnection> connectionHandler;
  private final Handler<Throwable> exceptionHandler;
  private final CompressionOptions[] compressionOptions;
  private final Function<String, String> encodingDetector;
  private final GlobalTrafficShapingHandler trafficShapingHandler;

  public HttpServerWorker(ContextInternal context,
                          Supplier<ContextInternal> streamContextSupplier,
                          HttpServerImpl server,
                          VertxInternal vertx,
                          HttpServerOptions options,
                          String serverOrigin,
                          boolean disableH2C,
                          Handler<HttpServerConnection> connectionHandler,
                          Handler<Throwable> exceptionHandler,
                          GlobalTrafficShapingHandler trafficShapingHandler) {

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
    this.disableH2C = disableH2C;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
    this.compressionOptions = compressionOptions;
    this.encodingDetector = compressionOptions != null ? new EncodingDetector(compressionOptions)::determineEncoding : null;
    this.trafficShapingHandler = trafficShapingHandler;
  }

  @Override
  public void accept(Channel ch, SslChannelProvider sslChannelProvider) {
    if (HAProxyMessageCompletionHandler.canUseProxyProtocol(options.isUseProxyProtocol())) {
      IdleStateHandler idle;
      io.netty.util.concurrent.Promise<Channel> p = ch.eventLoop().newPromise();
      ch.pipeline().addLast(new HAProxyMessageDecoder());
      if (options.getProxyProtocolTimeout() > 0) {
        ch.pipeline().addLast("idle", idle = new IdleStateHandler(0, 0, options.getProxyProtocolTimeout(), options.getProxyProtocolTimeoutUnit()));
      } else {
        idle = null;
      }
      ch.pipeline().addLast(new HAProxyMessageCompletionHandler(p));
      p.addListener((GenericFutureListener<Future<Channel>>) future -> {
        if (future.isSuccess()) {
          if (idle != null) {
            ch.pipeline().remove(idle);
          }
          configurePipeline(future.getNow(), sslChannelProvider);
        } else {
          //No need to close the channel.HAProxyMessageDecoder already did
          handleException(future.cause());
        }
      });
    } else {
      configurePipeline(ch, sslChannelProvider);
    }
  }

  private void configurePipeline(Channel ch, SslChannelProvider sslChannelProvider) {
    ChannelPipeline pipeline = ch.pipeline();
    if (options.isSsl()) {
      pipeline.addLast("ssl", sslChannelProvider.createServerHandler());
      ChannelPromise p = ch.newPromise();
      pipeline.addLast("handshaker", new SslHandshakeCompletionHandler(p));
      p.addListener(future -> {
        if (future.isSuccess()) {
          if (options.isUseAlpn()) {
            SslHandler sslHandler = pipeline.get(SslHandler.class);
            String protocol = sslHandler.applicationProtocol();
            if ("h2".equals(protocol)) {
              handleHttp2(ch);
            } else {
              handleHttp1(ch, sslChannelProvider);
            }
          } else {
            handleHttp1(ch, sslChannelProvider);
          }
        } else {
          handleException(future.cause());
        }
      });
    } else {
      if (disableH2C) {
        handleHttp1(ch, sslChannelProvider);
      } else {
        IdleStateHandler idle;
        int idleTimeout = options.getIdleTimeout();
        int readIdleTimeout = options.getReadIdleTimeout();
        int writeIdleTimeout = options.getWriteIdleTimeout();
        if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
          pipeline.addLast("idle", idle = new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
        } else {
          idle = null;
        }
        // Handler that detects whether the HTTP/2 connection preface or just process the request
        // with the HTTP 1.x pipeline to support H2C with prior knowledge, i.e a client that connects
        // and uses HTTP/2 in clear text directly without an HTTP upgrade.
        pipeline.addLast(new Http1xOrH2CHandler() {
          @Override
          protected void configure(ChannelHandlerContext ctx, boolean h2c) {
            if (idle != null) {
              // It will be re-added but this way we don't need to pay attention to order
              pipeline.remove(idle);
            }
            if (h2c) {
              handleHttp2(ctx.channel());
            } else {
              handleHttp1(ch, sslChannelProvider);
            }
          }

          @Override
          public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
              ctx.close();
            }
          }

          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            handleException(cause);
          }
        });
      }
    }
    if (trafficShapingHandler != null) {
      pipeline.addFirst("globalTrafficShaping", trafficShapingHandler);
    }
  }

  private void handleException(Throwable cause) {
    context.emit(cause, exceptionHandler);
  }

  private void handleHttp1(Channel ch, SslChannelProvider sslChannelProvider) {
    configureHttp1OrH2C(ch.pipeline(), sslChannelProvider);
  }

  private void sendServiceUnavailable(Channel ch) {
    ch.writeAndFlush(
      Unpooled.copiedBuffer("HTTP/1.1 503 Service Unavailable\r\n" +
        "Content-Length:0\r\n" +
        "\r\n", StandardCharsets.ISO_8859_1))
      .addListener(ChannelFutureListener.CLOSE);
  }

  private void handleHttp2(Channel ch) {
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = buildHttp2ConnectionHandler(context, connectionHandler);
    ch.pipeline().addLast("handler", handler);
    configureHttp2(ch.pipeline());
  }

  void configureHttp2(ChannelPipeline pipeline) {
    if (!server.requestAccept()) {
      // That should send an HTTP/2 go away
      pipeline.channel().close();
      return;
    }
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addBefore("handler", "idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
    }
  }

  VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler(ContextInternal ctx, Handler<HttpServerConnection> handler_) {
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnection>()
      .server(true)
      .useCompression(compressionOptions)
      .useDecompression(options.isDecompressionSupported())
      .initialSettings(options.getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ServerConnection conn = new Http2ServerConnection(ctx, streamContextSupplier, serverOrigin, connHandler, encodingDetector, options, metrics);
        if (metrics != null) {
          conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
        }
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

  private void configureHttp1OrH2C(ChannelPipeline pipeline, SslChannelProvider sslChannelProvider) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    if (HttpServerImpl.USE_FLASH_POLICY_HANDLER) {
      pipeline.addLast("flashpolicy", new FlashPolicyHandler());
    }
    pipeline.addLast("httpDecoder", new VertxHttpRequestDecoder(options));
    pipeline.addLast("httpEncoder", new VertxHttpResponseEncoder());
    if (options.isDecompressionSupported()) {
      pipeline.addLast("inflater", new HttpContentDecompressor(false));
    }
    if (options.isCompressionSupported()) {
      pipeline.addLast("deflater", new HttpChunkContentCompressor(compressionOptions));
    }
    if (options.isSsl() || options.isCompressionSupported() || !vertx.transport().supportFileRegion() || trafficShapingHandler != null) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
    }
    if (disableH2C) {
      configureHttp1(pipeline, sslChannelProvider);
    } else {
      pipeline.addLast("h2c", new Http1xUpgradeToH2CHandler(this, sslChannelProvider, options.isCompressionSupported(), options.isDecompressionSupported()));
    }
  }

  void configureHttp1(ChannelPipeline pipeline, SslChannelProvider sslChannelProvider) {
    if (!server.requestAccept()) {
      sendServiceUnavailable(pipeline.channel());
      return;
    }
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(
        streamContextSupplier,
        sslChannelProvider,
        options,
        chctx,
        context,
        serverOrigin,
        metrics);
      return conn;
    });
    pipeline.addLast("handler", handler);
    Http1xServerConnection conn = handler.getConnection();
    if (metrics != null) {
      conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
    }
    connectionHandler.handle(conn);
  }

  private static class EncodingDetector extends HttpContentCompressor {

    private EncodingDetector(CompressionOptions[] compressionOptions) {
      super(compressionOptions);
    }

    @Override
    protected String determineEncoding(String acceptEncoding) {
      return super.determineEncoding(acceptEncoding);
    }
  }
}
