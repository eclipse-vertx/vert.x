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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A channel provider that connects via a Proxy : HTTP or SOCKS
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyChannelProvider extends ChannelProvider {

  public static final ChannelProvider INSTANCE = new ProxyChannelProvider();

  private ProxyChannelProvider() {
  }

  @Override
  public void connect(VertxInternal vertx,
                      Bootstrap bootstrap,
                      ProxyOptions options,
                      SocketAddress remoteAddress,
                      Handler<Channel> channelInitializer,
                      Handler<AsyncResult<Channel>> channelHandler) {

    final String proxyHost = options.getHost();
    final int proxyPort = options.getPort();
    final String proxyUsername = options.getUsername();
    final String proxyPassword = options.getPassword();
    final ProxyType proxyType = options.getType();

    vertx.resolveAddress(proxyHost, dnsRes -> {
      if (dnsRes.succeeded()) {
        InetAddress address = dnsRes.result();
        InetSocketAddress proxyAddr = new InetSocketAddress(address, proxyPort);
        ProxyHandler proxy;

        switch (proxyType) {
          default:
          case HTTP:
            proxy = proxyUsername != null && proxyPassword != null
                ? new HttpProxyHandler(proxyAddr, proxyUsername, proxyPassword) : new HttpProxyHandler(proxyAddr);
            break;
          case SOCKS5:
            proxy = proxyUsername != null && proxyPassword != null
                ? new Socks5ProxyHandler(proxyAddr, proxyUsername, proxyPassword) : new Socks5ProxyHandler(proxyAddr);
            break;
          case SOCKS4:
            // SOCKS4 only supports a username and could authenticate the user via Ident
            proxy = proxyUsername != null ? new Socks4ProxyHandler(proxyAddr, proxyUsername)
                : new Socks4ProxyHandler(proxyAddr);
            break;
        }

        bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        java.net.SocketAddress targetAddress = vertx.transport().convert(remoteAddress, false);

        bootstrap.handler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addFirst("proxy", proxy);
            pipeline.addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof ProxyConnectionEvent) {
                  pipeline.remove(proxy);
                  pipeline.remove(this);
                  channelInitializer.handle(ch);
                  channelHandler.handle(Future.succeededFuture(ch));
                }
                ctx.fireUserEventTriggered(evt);
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                      throws Exception {
                channelHandler.handle(Future.failedFuture(cause));
              }
            });
          }
        });
        ChannelFuture future = bootstrap.connect(targetAddress);

        future.addListener(res -> {
          if (!res.isSuccess()) {
            channelHandler.handle(Future.failedFuture(res.cause()));
          }
        });
      } else {
        channelHandler.handle(Future.failedFuture(dnsRes.cause()));
      }
    });
  }
}
