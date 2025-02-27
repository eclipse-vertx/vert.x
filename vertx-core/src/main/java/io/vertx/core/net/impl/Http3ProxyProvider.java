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

package io.vertx.core.net.impl;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionAddress;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ProxyProvider {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http3ProxyProvider.class);

  private final EventLoop eventLoop;

  public Http3ProxyProvider(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
  }

  public Promise<QuicChannel> createProxyQuicChannel(InetSocketAddress proxyAddress,
                                                     InetSocketAddress remoteAddress) {
    Promise<QuicChannel> channelPromise = eventLoop.newPromise();

    Http3Utils.newDatagramChannel(eventLoop, proxyAddress, Http3Utils.newClientSslContext())
      .addListener((ChannelFutureListener) future -> {
        NioDatagramChannel channel = (NioDatagramChannel) future.channel();
        Http3Utils.newQuicChannel(channel, ch -> {
            ch.pipeline().addLast("myProxyConnectedHandler", new ProxyConnectedHandler(channel));
            ch.pipeline().addLast("proxy", new VertxSocks5ProxyHandler(proxyAddress, remoteAddress));
          })
          .addListener((GenericFutureListener<Future<QuicChannel>>) future1 -> {
            channelPromise.setSuccess(Http3Utils.getResultOrThrow(future1));
          });
      });
    return channelPromise;
  }

  private static class ProxyConnectedHandler extends ChannelOutboundHandlerAdapter {
    private final NioDatagramChannel channel;

    public ProxyConnectedHandler(NioDatagramChannel channel) {
      this.channel = channel;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) {
      logger.trace("Connect method called.");
      Http3Utils.newQuicChannel(channel, new Http3Utils.MyChannelInitializer())
        .addListener((GenericFutureListener<Future<QuicChannel>>) newQuicChannelFut -> {
          QuicConnectionAddress proxyAddress = newQuicChannelFut.get().remoteAddress();
          ctx.connect(proxyAddress, localAddress, promise);
        });
    }
  }
}
