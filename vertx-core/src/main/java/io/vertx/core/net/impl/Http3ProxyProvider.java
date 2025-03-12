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
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionAddress;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.proxy.HttpProxyHandler;
import io.vertx.core.internal.proxy.Socks5ProxyHandler;
import io.vertx.core.net.ProxyOptions;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ProxyProvider {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http3ProxyProvider.class);
  public static final String CHANNEL_HANDLER_PROXY = "myProxyHandler";
  public static final String CHANNEL_HANDLER_PROXY_CONNECTION = "myProxyConnectionEventHandler";
  private static final String CHANNEL_HANDLER_PROXY_CONNECTED = "myProxyConnectedHandler";
  private static final String CHANNEL_HANDLER_CLIENT_CONNECTION = "myHttp3ClientConnectionHandler";

  //TODO: This var is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  public static boolean IS_NETTY_PROXY_HANDLER_ALTERED = false;

  private final EventLoop eventLoop;

  public Http3ProxyProvider(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
  }

  //TODO: This method is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  public Future<Channel> createProxyQuicChannel(InetSocketAddress proxyAddress, InetSocketAddress remoteAddress,
                                                ProxyOptions proxyOptions) {
    if (IS_NETTY_PROXY_HANDLER_ALTERED) {
      return createProxyQuicChannelWithAlterationOnNetty(proxyAddress, remoteAddress, proxyOptions);
    }
    return createProxyQuicChannelWithoutAlterationOnNetty(proxyAddress, remoteAddress, proxyOptions);
  }

  private Future<Channel> createProxyQuicChannelWithAlterationOnNetty(InetSocketAddress proxyAddress,
                                                                      InetSocketAddress remoteAddress,
                                                                      ProxyOptions proxyOptions) {

    Promise<Channel> channelPromise = eventLoop.newPromise();

    io.vertx.core.internal.proxy.ProxyHandler proxyHandler = selectProxyHandler2(proxyOptions, proxyAddress,
      remoteAddress);

    Promise<Channel> quicStreamChannelPromise = eventLoop.newPromise();

    Http3Utils.newDatagramChannel(eventLoop, proxyAddress, Http3Utils.newClientSslContext())
      .addListener((ChannelFutureListener) future -> {
        NioDatagramChannel channel = (NioDatagramChannel) future.channel();
        Http3Utils.newQuicChannel(channel, new ChannelInitializer<QuicChannel>() {
            @Override
            protected void initChannel(QuicChannel ch) {
              ch.pipeline().addLast(CHANNEL_HANDLER_CLIENT_CONNECTION,
                new Http3Utils.Http3ClientConnectionHandlerBuilder()
                  .inboundControlStreamHandler(settingsFrame -> {
                    quicStreamChannelPromise.addListener((GenericFutureListener<Future<Channel>>) quicStreamChannelFut -> {
                      if (!quicStreamChannelFut.isSuccess()) {
                        channelPromise.setFailure(quicStreamChannelFut.cause());
                        return;
                      }

                      quicStreamChannelFut.get().pipeline().addLast(CHANNEL_HANDLER_PROXY, proxyHandler);
                      channelPromise.setSuccess(quicStreamChannelFut.get());
                    });
                  }).build());
            }
          })
          .addListener((GenericFutureListener<Future<QuicChannel>>) quicChannelFuture -> {
            if (!quicChannelFuture.isSuccess()) {
              channelPromise.setFailure(quicChannelFuture.cause());
              return;
            }
            Http3Utils.newRequestStream(quicChannelFuture.get(), quicStreamChannelPromise::setSuccess);
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

  private Promise<Channel> createProxyQuicChannelWithoutAlterationOnNetty(InetSocketAddress proxyAddress,
                                                                          InetSocketAddress remoteAddress,
                                                                          ProxyOptions proxyOptions) {
    Promise<Channel> channelPromise = eventLoop.newPromise();
    ChannelHandler proxyHandler = selectProxyHandler(proxyOptions, proxyAddress);
    ProxyHandlerWrapper proxy = new ProxyHandlerWrapper((ProxyHandler) proxyHandler, remoteAddress);

    Http3Utils.newDatagramChannel(eventLoop, proxyAddress, Http3Utils.newClientSslContext())
      .addListener((ChannelFutureListener) future -> {
        NioDatagramChannel channel = (NioDatagramChannel) future.channel();
        Http3Utils.newQuicChannel(channel, quicChannel -> {
          quicChannel.pipeline().addLast(new Http3Utils.Http3ClientConnectionHandlerBuilder().build());
        }).addListener((GenericFutureListener<Future<QuicChannel>>) quicChannelFut -> {
          QuicChannel quicChannel = quicChannelFut.get();
          Http3Utils.newRequestStream(quicChannel, streamChannel -> {
            quicChannel.pipeline().addLast(CHANNEL_HANDLER_PROXY_CONNECTED, new ProxyConnectedHandler(channel));
            quicChannel.pipeline().addLast(CHANNEL_HANDLER_PROXY, proxy);
          }).onComplete(event -> {
            if (!event.succeeded()) {
              channelPromise.setFailure(event.cause());
              return;
            }
            channelPromise.setSuccess(event.result());
          });
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

  public ChannelHandler selectProxyHandler(ProxyOptions proxyOptions, InetSocketAddress proxyAddr) {
    ChannelHandler proxy;

    switch (proxyOptions.getType()) {
      default:
      case HTTP:
        proxy = proxyOptions.getUsername() != null && proxyOptions.getPassword() != null
          ? new HttpProxyHandler(proxyAddr, proxyOptions.getUsername(), proxyOptions.getPassword()) :
          new HttpProxyHandler(proxyAddr);
        break;
      case SOCKS5:
        proxy = proxyOptions.getUsername() != null && proxyOptions.getPassword() != null
          ? new io.netty.handler.proxy.Socks5ProxyHandler(proxyAddr, proxyOptions.getUsername(),
          proxyOptions.getPassword()) : new io.netty.handler.proxy.Socks5ProxyHandler(proxyAddr);
        break;
      case SOCKS4:
        // SOCKS4 only supports a username and could authenticate the user via Ident
        proxy = proxyOptions.getUsername() != null ? new Socks4ProxyHandler(proxyAddr, proxyOptions.getUsername())
          : new Socks4ProxyHandler(proxyAddr);
        break;
    }
    return proxy;
  }

  public io.vertx.core.internal.proxy.ProxyHandler selectProxyHandler2(ProxyOptions proxyOptions,
                                                                       InetSocketAddress proxyAddr,
                                                                       InetSocketAddress destinationAddr) {
    io.vertx.core.internal.proxy.ProxyHandler proxy;

    switch (proxyOptions.getType()) {
      default:
      case HTTP:
        proxy = proxyOptions.getUsername() != null && proxyOptions.getPassword() != null
          ? new io.vertx.core.internal.proxy.HttpProxyHandler(proxyAddr, destinationAddr, proxyOptions.getUsername(),
          proxyOptions.getPassword()) : new io.vertx.core.internal.proxy.HttpProxyHandler(proxyAddr, destinationAddr);
        break;
      case SOCKS5:
        proxy = proxyOptions.getUsername() != null && proxyOptions.getPassword() != null ?
          new Socks5ProxyHandler(proxyAddr, destinationAddr, proxyOptions.getUsername(), proxyOptions.getPassword())
          : new Socks5ProxyHandler(proxyAddr, destinationAddr);
        break;
      case SOCKS4:
        // SOCKS4 only supports a username and could authenticate the user via Ident
        proxy = proxyOptions.getUsername() != null ? new io.vertx.core.internal.proxy.Socks4ProxyHandler(proxyAddr,
          destinationAddr, proxyOptions.getUsername())
          : new io.vertx.core.internal.proxy.Socks4ProxyHandler(proxyAddr, destinationAddr);
        break;
    }
    return proxy;
  }

  //TODO: This class is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  public static class ProxyHandlerWrapper extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(ProxyHandlerWrapper.class);

    private final io.netty.handler.proxy.ProxyHandler proxy;
    private final SocketAddress remoteAddress;

    public ProxyHandlerWrapper(io.netty.handler.proxy.ProxyHandler proxyHandler, SocketAddress remoteAddress) {
      this.proxy = proxyHandler;
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
