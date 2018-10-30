/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLHandshakeException;

/**
 * The logic for connecting to an host, this implementations performs a connection
 * to the host after resolving its internet address.
 *
 * See if we can replace that by a Netty handler sometimes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ChannelProvider {

  final Bootstrap bootstrap;
  final SSLHelper sslHelper;
  final ContextInternal context;
  final ProxyOptions options;
  private String applicationProtocol;
  private Channel channel;

  public ChannelProvider(Bootstrap bootstrap,
                         SSLHelper sslHelper,
                         ContextInternal context,
                         ProxyOptions options) {
    this.bootstrap = bootstrap;
    this.context = context;
    this.sslHelper = sslHelper;
    this.options = options;
  }

  public String applicationProtocol() {
    return applicationProtocol;
  }

  public Channel channel() {
    return channel;
  }

  public final void connect(boolean ssl,
                            SocketAddress remoteAddress,
                            String peerHost,
                            boolean forceSNI,
                            Handler<AsyncResult<Channel>> channelHandler) {
    doConnect(ssl, remoteAddress, peerHost, forceSNI, res -> {
      if (Context.isOnEventLoopThread()) {
        channelHandler.handle(res);
      } else {
        // We are on the GlobalEventExecutor
        context.nettyEventLoop().execute(() -> channelHandler.handle(res));
      }
    });
  }

  protected void initialize(boolean ssl, SocketAddress remoteAddress, String peerHost, boolean forceSNI, Channel ch) {
    if (ssl) {
      SslHandler sslHandler = new SslHandler(sslHelper.createEngine(context.owner(), peerHost, remoteAddress.port(), forceSNI ? peerHost : null));
      ch.pipeline().addLast("ssl", sslHandler);
    }
  }


  public void doConnect(boolean ssl, SocketAddress remoteAddress, String peerHost, boolean forceSNI, Handler<AsyncResult<Channel>> channelHandler) {
    VertxInternal vertx = context.owner();
    bootstrap.resolver(vertx.nettyAddressResolverGroup());
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) {
        initialize(ssl, remoteAddress, peerHost, forceSNI, ch);
      }
    });
    ChannelFuture fut = bootstrap.connect(vertx.transport().convert(remoteAddress, false));
    fut.addListener(res -> {
      if (res.isSuccess()) {
        cont(fut.channel(), channelHandler);
      } else {
        channelHandler.handle(io.vertx.core.Future.failedFuture(res.cause()));
      }
    });
  }

  protected void cont(Channel ch, Handler<AsyncResult<Channel>> channelHandler) {
    channel = ch;
    // TCP connected, so now we must do the SSL handshake if any
    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
    if (sslHandler != null) {
      sslHandler.handshakeFuture().addListener(future -> {
        if (future.isSuccess()) {
          applicationProtocol = sslHandler.applicationProtocol();
          channelHandler.handle(io.vertx.core.Future.succeededFuture(channel));
        } else {
          SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
          sslException.initCause(future.cause());
          channelHandler.handle(io.vertx.core.Future.failedFuture(sslException));
        }
      });
    } else {
      channelHandler.handle(io.vertx.core.Future.succeededFuture(channel));
    }
  }

}
