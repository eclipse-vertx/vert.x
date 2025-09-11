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
package io.vertx.core.quic.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.quic.QuicConnection;
import io.vertx.core.quic.QuicServer;
import io.vertx.core.quic.QuicServerOptions;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicServerImpl extends QuicEndpointImpl implements QuicServer {

  public static QuicServerImpl create(VertxInternal vertx, QuicServerOptions options) {
    return new QuicServerImpl(vertx, new QuicServerOptions(options));
  }

  private final QuicServerOptions options;
  private Handler<QuicConnection> handler;

  public QuicServerImpl(VertxInternal vertx, QuicServerOptions options) {
    super(vertx, options);
    this.options = options;
  }

  @Override
  public QuicServer handler(Handler<QuicConnection> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  protected QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider) {
    Mapping<? super String, ? extends SslContext> mapping = sslContextProvider.serverNameMapping(true);
    QuicSslContext sslContext = QuicSslContextBuilder.buildForServerWithSni(name -> (QuicSslContext) mapping.map(name));
    return new QuicServerCodecBuilder().sslContext(sslContext)
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
      // Setup a token handler. In a production system you would want to implement and provide your custom
      // one.
      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
      // ChannelHandler that is added into QuicChannel pipeline.
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          QuicChannel channel = (QuicChannel) ch;
          QuicConnectionHandler handler = new QuicConnectionHandler(context, QuicServerImpl.this.handler);
          ChannelPipeline pipeline = channel.pipeline();
          pipeline.addLast(handler);
        }

/*

        public void channelInactive(ChannelHandlerContext ctx) {
          // OK
          ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
//            if (f.isSuccess()) {
//              System.out.println("Connection closed: " + f.getNow());
//            }
            //only test for first connection
//            ctx.channel().parent().close();
          });
        }
*/
      });
  }

  @Override
  protected Future<SslContextProvider> createSslContextProvider(SslContextManager manager, ContextInternal context) {
    return manager.resolveSslContextProvider(options.getSslOptions(), context);
  }
}
