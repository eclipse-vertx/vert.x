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
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionAddress;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.Handler;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.proxy.HttpProxyHandler;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ProxyProvider {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http3ProxyProvider.class);
  public static final String CHANNEL_HANDLER_PROXY = "myProxyHandler";
  public static final String CHANNEL_HANDLER_PROXY_CONNECTED = "myProxyConnectedHandler";
  private static final String CHANNEL_HANDLER_SECONDARY_PROXY_CHANNEL = "mySecondProxyQuicChannelHandler";
  private static final String CHANNEL_HANDLER_CLIENT_CONNECTION = "myHttp3ClientConnectionHandler";

  //TODO: This var is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  public static boolean IS_NETTY_BASED_PROXY =  false;

  private final EventLoop eventLoop;

  public Http3ProxyProvider(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
  }

  //TODO: This method is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  public Future<Channel> createProxyQuicChannel(InetSocketAddress proxyAddress, InetSocketAddress remoteAddress,
                                                ProxyOptions proxyOptions) {
    Promise<Channel> channelPromise = eventLoop.newPromise();

    ChannelHandler proxyHandler = new ProxyHandlerSelector(proxyOptions, proxyAddress, remoteAddress).select();

    Http3Utils.newDatagramChannel(eventLoop, proxyAddress, Http3Utils.newClientSslContext())
      .addListener((ChannelFutureListener) future -> {
        NioDatagramChannel datagramChannel = (NioDatagramChannel) future.channel();
        if (IS_NETTY_BASED_PROXY) {
          if (proxyOptions.getType() == ProxyType.HTTP) {
            createNettyBasedHttpProxyQuicChannel(datagramChannel, proxyHandler, channelPromise
            );
          } else {
            createNettyBasedSocksProxyQuicChannel(datagramChannel, proxyHandler, channelPromise);
          }
        } else {
          if (proxyOptions.getType() == ProxyType.HTTP) {
            createVertxBasedHttpProxyQuicChannel(datagramChannel, proxyHandler, channelPromise);
          } else {
            createVertxBasedSocksProxyQuicChannel(datagramChannel, proxyHandler, channelPromise);
          }
        }
      });
    return channelPromise;
  }

  private void createVertxBasedHttpProxyQuicChannel(NioDatagramChannel datagramChannel, ChannelHandler proxyHandler,
                                                    Promise<Channel> channelPromise) {
    Promise<QuicStreamChannel> quicStreamChannelPromise = eventLoop.newPromise();
    Http3Utils.newQuicChannel(datagramChannel, quicChannel -> {
        quicChannel.pipeline().addLast(CHANNEL_HANDLER_CLIENT_CONNECTION,
          Http3Utils.newClientConnectionHandlerBuilder()
            .inboundControlStreamHandler(settingsFrame -> {
              quicStreamChannelPromise.addListener((GenericFutureListener<Future<QuicStreamChannel>>) quicStreamChannelFut -> {
                if (!quicStreamChannelFut.isSuccess()) {
                  channelPromise.setFailure(quicStreamChannelFut.cause());
                  return;
                }

                ChannelPipeline pipeline = quicStreamChannelFut.get().pipeline();
                pipeline.addLast(CHANNEL_HANDLER_PROXY, proxyHandler);
                pipeline.addLast(CHANNEL_HANDLER_PROXY_CONNECTED, new ProxyConnectedChannelHandler(channelPromise
                  , Http3ProxyProvider.this::removeProxyChannelHandlers));

              });
            }).build());
      })
      .addListener((GenericFutureListener<Future<QuicChannel>>) quicChannelFut -> {
        if (!quicChannelFut.isSuccess()) {
          channelPromise.setFailure(quicChannelFut.cause());
          return;
        }
        Http3Utils.newRequestStream(quicChannelFut.get(), quicStreamChannelPromise::setSuccess);
      });
  }

  private void createVertxBasedSocksProxyQuicChannel(NioDatagramChannel channel, ChannelHandler proxyHandler,
                                                     Promise<Channel> channelPromise) {
    Http3Utils.newQuicChannel(channel, ch -> {
        ch.pipeline().addLast(CHANNEL_HANDLER_PROXY, proxyHandler);
        ch.pipeline().addLast(CHANNEL_HANDLER_PROXY_CONNECTED, new ProxyConnectedChannelHandler(channelPromise
          , Http3ProxyProvider.this::removeProxyChannelHandlers));
      })
      .addListener((GenericFutureListener<Future<QuicChannel>>) quicChannelFut -> {
        if (!quicChannelFut.isSuccess()) {
          channelPromise.setFailure(quicChannelFut.cause());
          return;
        }
      });
  }

  private void createNettyBasedHttpProxyQuicChannel(NioDatagramChannel datagramChannel, ChannelHandler proxyHandler,
                                                    Promise<Channel> channelPromise) {
    Promise<QuicStreamChannel> quicStreamChannelPromise = eventLoop.newPromise();
    Http3Utils.newQuicChannel(datagramChannel, quicChannel -> {

      quicChannel.pipeline().addLast(CHANNEL_HANDLER_SECONDARY_PROXY_CHANNEL,
        new SecondProxyQuicChannelHandler(datagramChannel));
      quicChannel.pipeline().addLast(CHANNEL_HANDLER_PROXY, proxyHandler);

      quicChannel.pipeline().addLast(CHANNEL_HANDLER_CLIENT_CONNECTION,
        Http3Utils.newClientConnectionHandlerBuilder()
          .inboundControlStreamHandler(settingsFrame -> {
            quicStreamChannelPromise.addListener((GenericFutureListener<Future<QuicStreamChannel>>) quicStreamChannelFut -> {
              if (!quicStreamChannelFut.isSuccess()) {
                channelPromise.setFailure(quicStreamChannelFut.cause());
                return;
              }
            });
          }).build());
    }).addListener((GenericFutureListener<Future<QuicChannel>>) quicChannelFut -> {
      if (!quicChannelFut.isSuccess()) {
        channelPromise.setFailure(quicChannelFut.cause());
        return;
      }

      QuicChannel quicChannel = quicChannelFut.get();

      Http3Utils.newRequestStream(quicChannel, quicStreamChannelPromise::setSuccess).onComplete(event -> {

        QuicStreamChannel streamChannel = event.result();
        ChannelPipeline pipeline = streamChannel.pipeline();

        pipeline.addLast(CHANNEL_HANDLER_PROXY_CONNECTED,
          new ProxyConnectedChannelHandler(channelPromise, this::removeProxyChannelHandlers));

      });
    });
  }

  private void createNettyBasedSocksProxyQuicChannel(NioDatagramChannel datagramChannel, ChannelHandler proxyHandler,
                                                     Promise<Channel> channelPromise) {
    Http3Utils.newQuicChannel(datagramChannel, quicChannel -> {
      quicChannel.pipeline().addLast(CHANNEL_HANDLER_SECONDARY_PROXY_CHANNEL,
        new SecondProxyQuicChannelHandler(datagramChannel));
      quicChannel.pipeline().addLast(CHANNEL_HANDLER_PROXY, proxyHandler);
    }).addListener((GenericFutureListener<Future<QuicChannel>>) quicChannelFut -> {
      if (!quicChannelFut.isSuccess()) {
        channelPromise.setFailure(quicChannelFut.cause());
        return;
      }
      QuicChannel quicChannel = quicChannelFut.get();
      quicChannel.pipeline().addLast(CHANNEL_HANDLER_PROXY_CONNECTED,
        new ProxyConnectedChannelHandler(channelPromise, this::removeProxyChannelHandlers));
    });
  }

  private void removeProxyChannelHandlers(ChannelPipeline pipeline) {
    if (pipeline.get(CHANNEL_HANDLER_SECONDARY_PROXY_CHANNEL) != null) {
      pipeline.remove(CHANNEL_HANDLER_SECONDARY_PROXY_CHANNEL);
    }
    pipeline.remove(CHANNEL_HANDLER_PROXY);
  }

  public ChannelHandler selectProxyHandler(ProxyOptions proxyOptions, InetSocketAddress proxyAddr,
                                           InetSocketAddress destinationAddr) {
    return new ProxyHandlerSelector(proxyOptions, proxyAddr, destinationAddr).select();
  }

  private static class ProxyHandlerSelector {
    private final ProxyOptions proxyOptions;
    private final SocketAddress proxyAddr;
    private final SocketAddress destinationAddr;
    private final String username;
    private final String password;

    public ProxyHandlerSelector(ProxyOptions proxyOptions, SocketAddress proxyAddr, SocketAddress destinationAddr) {
      this.proxyOptions = proxyOptions;
      this.proxyAddr = proxyAddr;
      this.destinationAddr = destinationAddr;
      this.username = proxyOptions.getUsername();
      this.password = proxyOptions.getPassword();
    }

    public ChannelHandler select() {
      if (IS_NETTY_BASED_PROXY) {
        io.netty.handler.proxy.ProxyHandler proxyHandler;
        if (isHttp() && hasCredential()) {
          proxyHandler = new io.netty.handler.proxy.HttpProxyHandler(proxyAddr, username, password);
        } else if (isHttp() && !hasCredential()) {
          proxyHandler = new io.netty.handler.proxy.HttpProxyHandler(proxyAddr);
        } else if (isSocks5() && hasCredential()) {
          proxyHandler = new io.netty.handler.proxy.Socks5ProxyHandler(proxyAddr, username, password);
        } else if (isSocks5() && !hasCredential()) {
          proxyHandler = new io.netty.handler.proxy.Socks5ProxyHandler(proxyAddr);
        } else if (isSocks4() && hasCredential()) {
          proxyHandler = new io.netty.handler.proxy.Socks4ProxyHandler(proxyAddr, username);
        } else if (isSocks4() && !hasCredential()) {
          proxyHandler = new io.netty.handler.proxy.Socks4ProxyHandler(proxyAddr);
        } else {
          throw new RuntimeException("Not Supported");
        }
        proxyHandler.setConnectTimeoutMillis(proxyOptions.getConnectTimeoutUnit().toMillis(proxyOptions.getConnectTimeout()));
        return new ProxyHandlerWrapper(proxyHandler, destinationAddr);
      }
      io.vertx.core.internal.proxy.ProxyHandler proxyHandler;

      if (isHttp() && hasCredential()) {
        proxyHandler = new HttpProxyHandler(proxyAddr, username, password).asHttp3();
      } else if (isHttp() && !hasCredential()) {
        proxyHandler = new io.vertx.core.internal.proxy.HttpProxyHandler(proxyAddr).asHttp3();
      } else if (isSocks5() && hasCredential()) {
        proxyHandler = new io.vertx.core.internal.proxy.Socks5ProxyHandler(proxyAddr, username, password);
      } else if (isSocks5() && !hasCredential()) {
        proxyHandler = new io.vertx.core.internal.proxy.Socks5ProxyHandler(proxyAddr);
      } else if (isSocks4() && hasCredential()) {
        proxyHandler = new io.vertx.core.internal.proxy.Socks4ProxyHandler(proxyAddr, username);
      } else if (isSocks4() && !hasCredential()) {
        proxyHandler = new io.vertx.core.internal.proxy.Socks4ProxyHandler(proxyAddr);
      } else {
        throw new RuntimeException("Not Supported");
      }
      proxyHandler.setConnectTimeoutMillis(proxyOptions.getConnectTimeoutUnit().toMillis(proxyOptions.getConnectTimeout()));
      proxyHandler.destinationAddress(destinationAddr);
      return proxyHandler;
    }

    private boolean isSocks4() {
      return proxyOptions.getType() == ProxyType.SOCKS4;
    }

    private boolean isSocks5() {
      return proxyOptions.getType() == ProxyType.SOCKS5;
    }

    private boolean isHttp() {
      return proxyOptions.getType() == ProxyType.HTTP;
    }

    private boolean hasCredential() {
      return username != null && (proxyOptions.getType() == ProxyType.SOCKS4 || password != null);
    }
  }

  //TODO: This class is removed once Netty accepts our PR to add the destination to the ProxyHandler constructor.
  private static class SecondProxyQuicChannelHandler extends ChannelOutboundHandlerAdapter {
    private final NioDatagramChannel channel;

    public SecondProxyQuicChannelHandler(NioDatagramChannel channel) {
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

  private static class ProxyConnectedChannelHandler extends ChannelInboundHandlerAdapter {

    private final Promise<Channel> channelPromise;
    private final Handler<ChannelPipeline> proxyChannelHandlerRemover;

    public ProxyConnectedChannelHandler(Promise<Channel> channelPromise,
                                        Handler<ChannelPipeline> proxyChannelHandlerRemover) {
      this.channelPromise = channelPromise;
      this.proxyChannelHandlerRemover = proxyChannelHandlerRemover;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
      ChannelPipeline pipeline = ctx.pipeline();
      if (evt instanceof ProxyConnectionEvent) {
        proxyChannelHandlerRemover.handle(pipeline);
        pipeline.remove(this);

        if (ctx.channel() instanceof QuicStreamChannel) {
          channelPromise.setSuccess(ctx.channel().parent());
        } else {
          channelPromise.setSuccess(ctx.channel());
        }
      }
      ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.error("Proxy connection failed!");
      channelPromise.tryFailure(cause);
    }
  }
}
