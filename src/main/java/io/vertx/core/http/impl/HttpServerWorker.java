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
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.SslHandshakeCompletionHandler;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.impl.HAProxyMessageCompletionHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.nio.charset.StandardCharsets;

/**
 * A channel initializer that will takes care of configuring a blank channel for HTTP
 * to Vert.x {@link io.vertx.core.http.HttpServerRequest}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerWorker implements Handler<Channel> {

  final ContextInternal context;
  private final VertxInternal vertx;
  private final HttpServerImpl server;
  private final SSLHelper sslHelper;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final boolean logEnabled;
  private final boolean disableH2C;
  final Handler<HttpServerConnection> connectionHandler;
  private final Handler<Throwable> exceptionHandler;

  public HttpServerWorker(ContextInternal context,
                          HttpServerImpl server,
                          VertxInternal vertx,
                          SSLHelper sslHelper,
                          HttpServerOptions options,
                          String serverOrigin,
                          boolean disableH2C,
                          Handler<HttpServerConnection> connectionHandler,
                          Handler<Throwable> exceptionHandler) {
    this.context = context;
    this.server = server;
    this.vertx = vertx;
    this.sslHelper = sslHelper;
    this.options = options;
    this.serverOrigin = serverOrigin;
    this.logEnabled = options.getLogActivity();
    this.disableH2C = disableH2C;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void handle(Channel ch) {
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
          configurePipeline(future.getNow());
        } else {
          //No need to close the channel.HAProxyMessageDecoder already did
          handleException(future.cause());
        }
      });
    } else {
      configurePipeline(ch);
    }
  }

  private void configurePipeline(Channel ch) {
    ChannelPipeline pipeline = ch.pipeline();
    if (sslHelper.isSSL()) {
      if (options.isSni()) {
        SniHandler sniHandler = new SniHandler(sslHelper.serverNameMapper(vertx));
        pipeline.addLast(sniHandler);
      } else {
        SslHandler handler = new SslHandler(sslHelper.createEngine(vertx));
        handler.setHandshakeTimeout(sslHelper.getSslHandshakeTimeout(), sslHelper.getSslHandshakeTimeoutUnit());
        pipeline.addLast("ssl", handler);
      }
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
              handleHttp1(ch);
            }
          } else {
            handleHttp1(ch);
          }
        } else {
          handleException(future.cause());
        }
      });
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
            handleException(cause);
          }
        });
      }
    }
  }

  private void handleException(Throwable cause) {
    context.dispatch(cause, exceptionHandler);
  }

  private void handleHttp1(Channel ch) {
    configureHttp1OrH2C(ch.pipeline());
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
    if (options.getIdleTimeout() > 0) {
      pipeline.addBefore("handler", "idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
  }

  VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler(ContextInternal ctx, Handler<HttpServerConnection> handler_) {
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
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

  private void configureHttp1OrH2C(ChannelPipeline pipeline) {
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
      configureHttp1(pipeline);
    } else {
      pipeline.addLast("h2c", new Http1xUpgradeToH2CHandler(this, options.isCompressionSupported(), options.isDecompressionSupported()));
    }
  }

  void configureHttp1(ChannelPipeline pipeline) {
    if (!server.requestAccept()) {
      sendServiceUnavailable(pipeline.channel());
      return;
    }
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(context.owner(),
        sslHelper,
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
}
