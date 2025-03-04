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

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionAddress;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.internal.Socks5ProxyHandler;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ProxyProvider {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http3ProxyProvider.class);
  private static final String CHANNEL_HANDLER_PROXY = "proxy";
  private static final String CHANNEL_HANDLER_PROXY_CONNECTED = "myProxyConnectedHandler";

  public static boolean IS_NETTY_PROXY_HANDLER_ALTERED = true;

  private final EventLoop eventLoop;

  public Http3ProxyProvider(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
  }

  public Future<QuicChannel> createProxyQuicChannel(InetSocketAddress proxyAddress,
                                                    InetSocketAddress remoteAddress, Promise<QuicChannel> channelPromise) {
    Http3Utils.newDatagramChannel(eventLoop, proxyAddress, Http3Utils.newClientSslContext())
      .addListener((ChannelFutureListener) future -> {
        NioDatagramChannel channel = (NioDatagramChannel) future.channel();
        Http3Utils.newQuicChannel(channel, ch -> {
            ch.pipeline().addLast("proxy", new Socks5ProxyHandler(proxyAddress, remoteAddress));
          })
          .addListener((GenericFutureListener<Future<QuicChannel>>) future1 -> {
            if (!future1.isSuccess()) {
              channelPromise.setFailure(future1.cause());
              return;
            }
            channelPromise.setSuccess(future1.get());
          });
      });
    return channelPromise;
  }

  public void removeProxyChannelHandlers(ChannelPipeline pipeline) {
    if (pipeline.get(CHANNEL_HANDLER_PROXY_CONNECTED) != null) {
      pipeline.remove(CHANNEL_HANDLER_PROXY_CONNECTED);
    }
    pipeline.remove(CHANNEL_HANDLER_PROXY);
  }


  //TODO: This method is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  public Future<QuicChannel> createProxyQuicChannel(InetSocketAddress proxyAddress,
                                                    InetSocketAddress remoteAddress) {
    Promise<QuicChannel> channelPromise = eventLoop.newPromise();
    if (IS_NETTY_PROXY_HANDLER_ALTERED) {
      return createProxyQuicChannel(proxyAddress, remoteAddress, channelPromise);
    }

    Http3Utils.newDatagramChannel(eventLoop, proxyAddress, Http3Utils.newClientSslContext())
      .addListener((ChannelFutureListener) future -> {
        NioDatagramChannel channel = (NioDatagramChannel) future.channel();
        Http3Utils.newQuicChannel(channel, ch -> {
            ch.pipeline().addLast(CHANNEL_HANDLER_PROXY_CONNECTED, new ProxyConnectedHandler(channel));
            ch.pipeline().addLast(CHANNEL_HANDLER_PROXY, new VertxSocks5ProxyHandler(proxyAddress, remoteAddress));
          })
          .addListener((GenericFutureListener<Future<QuicChannel>>) future1 -> {
            if (!future1.isSuccess()) {
              channelPromise.setFailure(future1.cause());
              return;
            }
            channelPromise.setSuccess(future1.get());
          });
      });
    return channelPromise;
  }

  //TODO: This class is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
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

  //TODO: This class is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  private static class VertxSocks5ProxyHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(VertxSocks5ProxyHandler.class);

    private final io.netty.handler.proxy.Socks5ProxyHandler proxy;
    private final SocketAddress remoteAddress;

    public VertxSocks5ProxyHandler(SocketAddress proxyAddress, SocketAddress remoteAddress) {
      this.proxy = new io.netty.handler.proxy.Socks5ProxyHandler(proxyAddress);
      this.remoteAddress = remoteAddress;
    }

    @Override
    public final void connect(ChannelHandlerContext ctx, SocketAddress ignored, SocketAddress localAddress,
                              ChannelPromise promise) throws Exception {
      log.trace("Connect method called.");
      proxy.connect(ctx, this.remoteAddress, localAddress, promise);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      log.trace("handlerAdded method called.");
      proxy.handlerAdded(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      proxy.channelActive(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      proxy.write(ctx, msg, promise);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
      proxy.bind(ctx, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
      proxy.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
      proxy.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
      proxy.deregister(ctx, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
      proxy.read(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
      proxy.flush(ctx);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
      proxy.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
      proxy.channelUnregistered(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      proxy.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      proxy.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      proxy.channelReadComplete(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      proxy.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
      proxy.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      proxy.exceptionCaught(ctx, cause);
    }

    @Override
    public boolean isSharable() {
      return proxy.isSharable();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
      proxy.handlerRemoved(ctx);
    }
  }
}
