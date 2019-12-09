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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.HandlerHolder;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.SslHandshakeCompletionHandler;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * A channel initializer that will takes care of configuring a blank channel for HTTP
 * to Vert.x {@link io.vertx.core.http.HttpServerRequest}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */public class HttpServerChannelInitializer extends ChannelInitializer<Channel> {

  private final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final HttpServerMetrics metrics;
  private final boolean logEnabled;
  private final boolean disableH2C;
  private final Function<EventLoop, HandlerHolder<? extends Handler<HttpServerConnection>>> connectionHandler;
  private final Function<EventLoop, HandlerHolder<? extends Handler<Throwable>>> errorHandler;

  public HttpServerChannelInitializer(VertxInternal vertx,
                                      SSLHelper sslHelper,
                                      HttpServerOptions options,
                                      String serverOrigin,
                                      HttpServerMetrics metrics,
                                      boolean disableH2C,
                                      Function<EventLoop, HandlerHolder<? extends Handler<HttpServerConnection>>> connectionHandler,
                                      Function<EventLoop, HandlerHolder<? extends Handler<Throwable>>> errorHandler) {
    this.vertx = vertx;
    this.sslHelper = sslHelper;
    this.options = options;
    this.serverOrigin = serverOrigin;
    this.metrics = metrics;
    this.logEnabled = options.getLogActivity();
    this.disableH2C = disableH2C;
    this.connectionHandler = connectionHandler;
    this.errorHandler = errorHandler;
  }

  @Override
  protected void initChannel(Channel ch) {
    ChannelPipeline pipeline = ch.pipeline();
    if (sslHelper.isSSL()) {
      ch.pipeline().addFirst("handshaker", new SslHandshakeCompletionHandler(ar -> {
        if (ar.succeeded()) {
          if (options.isUseAlpn()) {
            SslHandler sslHandler = pipeline.get(SslHandler.class);
            String protocol = sslHandler.applicationProtocol();
            if ("h2".equals(protocol)) {
              handleHttp2(ch);
            } else {
              handleHttp1(ch);
            }
          } else {
            handleHttp1(ch);
          }
        } else {
          handleException(ch, ar.cause());
        }
      }));
      if (options.isSni()) {
        SniHandler sniHandler = new SniHandler(sslHelper.serverNameMapper(vertx));
        pipeline.addFirst(sniHandler);
      } else {
        SslHandler handler = new SslHandler(sslHelper.createEngine(vertx));
        pipeline.addFirst("ssl", handler);
      }
    } else {
      if (disableH2C) {
        handleHttp1(ch);
      } else {
        IdleStateHandler idle;
        if (options.getIdleTimeout() > 0) {
          pipeline.addLast("idle", idle = new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
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
              handleHttp1(ch);
            }
          }

          @Override
          public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
              ctx.close();
            }
          }

          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            handleException(ch, cause);
          }
        });
      }
    }
  }

  private void handleException(Channel ch, Throwable cause) {
    HandlerHolder<? extends Handler<Throwable>> holder = errorHandler.apply(ch.eventLoop());
    if (holder != null) {
      holder.context.dispatch(cause, holder.handler);
    }
  }

  private void handleHttp1(Channel ch) {
    HandlerHolder<? extends Handler<HttpServerConnection>> holder = connectionHandler.apply(ch.eventLoop());
    if (holder == null) {
      sendServiceUnavailable(ch);
      return;
    }
    configureHttp1OrH2C(ch.pipeline(), holder);
  }

  private void sendServiceUnavailable(Channel ch) {
    ch.writeAndFlush(
      Unpooled.copiedBuffer("HTTP/1.1 503 Service Unavailable\r\n" +
        "Content-Length:0\r\n" +
        "\r\n", StandardCharsets.ISO_8859_1))
      .addListener(ChannelFutureListener.CLOSE);
  }

  private void handleHttp2(Channel ch) {
    HandlerHolder<? extends Handler<HttpServerConnection>> holder = connectionHandler.apply(ch.eventLoop());
    if (holder == null) {
      ch.close();
      return;
    }
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = buildHttp2ConnectionHandler(holder.context, holder.handler);
    ch.pipeline().addLast("handler", handler);
    configureHttp2(ch.pipeline());
  }

  void configureHttp2(ChannelPipeline pipeline) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addBefore("handler", "idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
  }

  VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler(ContextInternal ctx, Handler<HttpServerConnection> handler_) {
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnection>()
      .server(true)
      .useCompression(options.isCompressionSupported())
      .useDecompression(options.isDecompressionSupported())
      .compressionLevel(options.getCompressionLevel())
      .initialSettings(options.getInitialSettings())
      .connectionFactory(connHandler -> new Http2ServerConnection(ctx, serverOrigin, connHandler, options, metrics))
      .logEnabled(logEnabled)
      .build();
    handler.addHandler(conn -> {
      if (metrics != null) {
        conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
      }
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      handler_.handle(conn);
    });
    return handler;
  }

  private void configureHttp1OrH2C(ChannelPipeline pipeline, HandlerHolder<? extends Handler<HttpServerConnection>> holder) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
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
      pipeline.addLast("deflater", new HttpChunkContentCompressor(options.getCompressionLevel()));
    }
    if (sslHelper.isSSL() || options.isCompressionSupported()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
    if (disableH2C) {
      configureHttp1(pipeline, holder);
    } else {
      pipeline.addLast("h2c", new Http1xUpgradeToH2CHandler(this, holder, options.isCompressionSupported(), options.isDecompressionSupported()));
    }
  }

  void configureHttp1(ChannelPipeline pipeline, HandlerHolder<? extends Handler<HttpServerConnection>> holder) {
    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(holder.context.owner(),
        sslHelper,
        options,
        chctx,
        holder.context,
        serverOrigin,
        metrics);
      return conn;
    });
    pipeline.addLast("handler", handler);
    Http1xServerConnection conn = handler.getConnection();
    if (metrics != null) {
      conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
    }
    holder.handler.handle(conn);
  }
}
