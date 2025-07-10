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

package io.vertx.core.http.impl.http2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.http.impl.HttpServerConnectionInitializer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.net.impl.VertxHandler;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class Http1xUpgradeToH2CHandler extends ChannelInboundHandlerAdapter {

  private final HttpServerConnectionInitializer initializer;
  private final ContextInternal context;
  private final Http2CodecServerChannelInitializer provider;
  private final SslChannelProvider sslChannelProvider;
  private final SslContextManager sslContextManager;
  private VertxHttp2ConnectionHandler<Http2ServerConnectionImpl> handler;
  private final boolean isCompressionSupported;
  private final boolean isDecompressionSupported;

  public Http1xUpgradeToH2CHandler(HttpServerConnectionInitializer initializer, ContextInternal context, Http2CodecServerChannelInitializer provider, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager, boolean isCompressionSupported, boolean isDecompressionSupported) {
    this.initializer = initializer;
    this.context = context;
    this.provider = provider;
    this.sslChannelProvider = sslChannelProvider;
    this.sslContextManager = sslContextManager;
    this.isCompressionSupported = isCompressionSupported;
    this.isDecompressionSupported = isDecompressionSupported;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) msg;
      if (request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, true)) {
        String connection = request.headers().get(io.vertx.core.http.HttpHeaders.CONNECTION);
        int found = 0;
        if (connection != null && connection.length() > 0) {
          StringBuilder buff = new StringBuilder();
          int pos = 0;
          int len = connection.length();
          while (pos < len) {
            char c = connection.charAt(pos++);
            if (c != ' ' && c != ',') {
              buff.append(Character.toLowerCase(c));
            }
            if (c == ',' || pos == len) {
              if (buff.indexOf("upgrade") == 0 && buff.length() == 7) {
                found |= 1;
              } else if (buff.indexOf("http2-settings") == 0 && buff.length() == 14) {
                found |= 2;
              }
              buff.setLength(0);
            }
          }
        }
        if (found == 3) {
          String settingsHeader = request.headers().get(Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER);
          if (settingsHeader != null) {
            Http2Settings settings = HttpUtils.decodeSettings(settingsHeader);
            if (settings != null) {
              ChannelPipeline pipeline = ctx.pipeline();
              if (pipeline.get("chunkedWriter") != null) {
                pipeline.remove("chunkedWriter");
              }
              DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, SWITCHING_PROTOCOLS, Unpooled.EMPTY_BUFFER, false);
              res.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
              res.headers().add(HttpHeaderNames.UPGRADE, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME);
              ctx.write(res);
              pipeline.remove("httpEncoder");
              if (isCompressionSupported) {
                pipeline.remove("deflater");
              }
              if (isDecompressionSupported) {
                pipeline.remove("inflater");
              }
              handler = provider.buildHttp2ConnectionHandler(context);
              pipeline.replace(VertxHandler.class, "handler", handler);
              handler.serverUpgrade(ctx, settings);
              DefaultHttp2Headers headers = new DefaultHttp2Headers();
              headers.method(request.method().name());
              headers.path(request.uri());
              headers.authority(request.headers().get("host"));
              headers.scheme("http");
              request.headers().remove("http2-settings");
              request.headers().remove("host");
              request.headers().forEach(header -> {
                if (!HttpHeaderValidationUtil.isConnectionHeader(header.getKey(), true)) {
                  headers.add(header.getKey().toLowerCase(), header.getValue());
                }
              });
              ctx.fireChannelRead(new DefaultHttp2HeadersFrame(headers, false));
            }
          }
        }
        if (handler == null) {
          DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST, Unpooled.EMPTY_BUFFER, false);
          res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
          ctx.writeAndFlush(res);
        }
      } else {
        initializer.configureHttp1Handler(ctx.pipeline(), sslContextManager);
        ctx.fireChannelRead(msg);
        ctx.pipeline().remove(this);
      }
    } else {
      if (handler != null) {
        if (msg instanceof HttpContent) {
          HttpContent content = (HttpContent) msg;
          ByteBuf buf = VertxHandler.safeBuffer(content.content());
          boolean end = msg instanceof LastHttpContent;
          ctx.fireChannelRead(new DefaultHttp2DataFrame(buf, end, 0));
          if (end) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.remove("h2c");
            pipeline.remove("httpDecoder");
            initializer.checkAccept(pipeline);
          }
        } else {
          // We might have left over buffer sent when removing the HTTP decoder that needs to be propagated to the HTTP handler
          super.channelRead(ctx, msg);
        }
      }
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
      ctx.close();
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
}
