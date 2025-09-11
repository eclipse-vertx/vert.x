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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClosedChannelException;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.QuicOptions;
import io.vertx.core.net.SocketAddress;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;

import static io.vertx.core.net.impl.QuicProxyProvider.*;

/**
 * The logic for connecting to an host, this implementations performs a connection
 * to the host after resolving its internet address.
 *
 * See if we can replace that by a Netty handler sometimes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class ChannelProvider {
  private static final Logger log = LoggerFactory.getLogger(ChannelProvider.class);

  public static final String CLIENT_SSL_HANDLER_NAME = "ssl";
  private final Bootstrap bootstrap;
  private final SslContextProvider sslContextProvider;
  private final ContextInternal context;
  private final NetClientOptions clientOptions;
  private final int connectTimeout;
  private ProxyOptions proxyOptions;
  private Handler<Channel> handler;
  private final boolean supportsQuic;

  public ChannelProvider(Bootstrap bootstrap,
                         SslContextProvider sslContextProvider,
                         ContextInternal context,
                         NetClientOptions clientOptions,
                         int connectTimeout,
                         boolean supportsQuic) {
    this.bootstrap = bootstrap;
    this.context = context;
    this.sslContextProvider = sslContextProvider;
    this.clientOptions = clientOptions;
    this.connectTimeout = connectTimeout;
    this.supportsQuic = supportsQuic;
  }

  /**
   * Set the proxy options to use.
   *
   * @param proxyOptions the proxy options to use
   * @return fluently this
   */
  public ChannelProvider proxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  /**
   * Set a handler called when the channel has been established.
   *
   * @param handler the channel handler
   * @return fluently this
   */
  public ChannelProvider handler(Handler<Channel> handler) {
    this.handler = handler;
    return this;
  }

  public Future<Channel> connect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, QuicOptions quicOptions) {
    Promise<Channel> p = context.nettyEventLoop().newPromise();
    connect(handler, remoteAddress, peerAddress, serverName, ssl, sslOptions, quicOptions, p);
    return p;
  }

  private void connect(Handler<Channel> handler, SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, QuicOptions quicOptions, Promise<Channel> p) {
    try {
      if (supportsQuic) {
        bootstrap.channelFactory(() -> context.owner().transport().datagramChannel());
      } else {
        bootstrap.channelFactory(context.owner().transport().channelFactory(remoteAddress.isDomainSocket()));
      }
    } catch (Exception e) {
      p.setFailure(e);
      return;
    }
    if (proxyOptions != null) {
      handleProxyConnect(handler, remoteAddress, peerAddress, serverName, ssl, sslOptions, quicOptions, p);
    } else {
      handleConnect(handler, remoteAddress, peerAddress, serverName, ssl, sslOptions, p);
    }
  }

  private void initSSL(Handler<Channel> handler, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, Channel ch, Promise<Channel> channelHandler) {
    if (ssl) {
      SslChannelProvider sslChannelProvider = new SslChannelProvider(context.owner(), sslContextProvider, false);
      ChannelHandler sslHandler = sslChannelProvider.createClientSslHandler(peerAddress, serverName, sslOptions.isUseAlpn(),
        sslOptions.getSslHandshakeTimeout(), sslOptions.getSslHandshakeTimeoutUnit());
      ChannelPipeline pipeline = ch.pipeline();
      pipeline.addLast(CLIENT_SSL_HANDLER_NAME, sslHandler);
      pipeline.addLast(new ChannelDuplexHandler() {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
          if (cause instanceof PortUnreachableException) {
            cause = new VertxConnectException(cause);
          }
          channelHandler.tryFailure(cause);
          ctx.close();

          super.exceptionCaught(ctx, cause);
        }
      });

      if (!supportsQuic) {
        Promise<ChannelHandlerContext> promise = context.nettyEventLoop().newPromise();
        promise.addListener((GenericFutureListener<Future<ChannelHandlerContext>>) future -> {
          if (!future.isSuccess()) {
            channelHandler.setFailure(future.cause());
            return;
          }

          ChannelHandlerContext ctx = future.get();
          connected(ctx.channel(), channelHandler);
        });
        pipeline.addLast(new HttpSslHandshaker(promise));
      }
    }
  }


  private void handleConnect(Handler<Channel> handler, SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, Promise<Channel> channelHandler) {
    VertxInternal vertx = context.owner();
    bootstrap.resolver(vertx.nameResolver().nettyAddressResolverGroup());
    bootstrap.handler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        initSSL(handler, peerAddress, serverName, ssl, sslOptions, ch, channelHandler);
      }
    });
    java.net.SocketAddress convert = vertx.transport().convert(remoteAddress);
    ChannelFuture fut = bootstrap.connect(convert);
    fut.addListener(res -> {
      if (!res.isSuccess()) {
        channelHandler.tryFailure(res.cause());
        return;
      }
      if (!supportsQuic) {
        if (!ssl) {
          connected(fut.channel(), channelHandler);
        }
        return;
      }
      NioDatagramChannel nioDatagramChannel = (NioDatagramChannel) fut.channel();

      QuicUtils.newQuicChannel(nioDatagramChannel, quicChannel -> {
        context.owner().transport().configure(clientOptions, connectTimeout, quicChannel);
        Promise<ChannelHandlerContext> promise = context.nettyEventLoop().newPromise();
        promise.addListener((GenericFutureListener<Future<ChannelHandlerContext>>) future -> {
          if (!future.isSuccess()) {
            channelHandler.setFailure(future.cause());
            return;
          }

          ChannelHandlerContext ctx = future.get();
          connected(ctx.channel(), channelHandler);
        });

        quicChannel.pipeline().addLast(new HttpSslHandshaker(promise));
        quicChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
          @Override
          public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            log.info(String.format("%s triggered in QuicChannel handler", evt.getClass().getSimpleName()));
            super.userEventTriggered(ctx, evt);
          }
        });
      })
      .addListener((io.netty.util.concurrent.Future<QuicChannel> future) -> {
        if (!future.isSuccess() && !channelHandler.isDone()) {
          Throwable cause = future.cause();
          if(future.cause() instanceof QuicClosedChannelException) {
            cause = new ConnectTimeoutException(future.cause().getMessage());
          }
          channelHandler.tryFailure(cause);
        }
      });
    });
  }

  /**
   * Signal we are connected to the remote server.
   *
   * @param channel the channel
   * @param channelHandler the channel handler
   */
  private void connected(Channel channel, Promise<Channel> channelHandler) {
    if (handler != null) {
      context.dispatch(channel, handler);
    }
    channelHandler.setSuccess(channel);
  }

  /**
   * A channel provider that connects via a Proxy : HTTP or SOCKS
   */
  private void handleProxyConnect(Handler<Channel> handler, SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, QuicOptions quicOptions, Promise<Channel> channelHandler) {

    final VertxInternal vertx = context.owner();
    final String proxyHost = proxyOptions.getHost();
    final int proxyPort = proxyOptions.getPort();

    vertx.nameResolver().resolve(proxyHost).onComplete(dnsRes -> {
      if (dnsRes.succeeded()) {
        InetAddress address = dnsRes.result();
        InetSocketAddress proxyAddr = new InetSocketAddress(address, proxyPort);
        QuicProxyProvider proxyProvider = new QuicProxyProvider(context.nettyEventLoop());

        if (sslOptions != null && supportsQuic) {
          bootstrap.resolver(vertx.nameResolver().nettyAddressResolverGroup());
          java.net.SocketAddress targetAddress = vertx.transport().convert(remoteAddress);

          proxyProvider.createProxyQuicChannel(proxyAddr, (InetSocketAddress) targetAddress, proxyOptions, quicOptions, sslOptions)
            .addListener((GenericFutureListener<Future<Channel>>) channelFuture -> {
              if (!channelFuture.isSuccess()) {
                channelHandler.tryFailure(channelFuture.cause());
                return;
              }
              connected(channelFuture.get(), channelHandler);
            });
          return;
        }

        bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        java.net.SocketAddress targetAddress = vertx.transport().convert(remoteAddress);
        ChannelHandler proxy = proxyProvider.selectProxyHandler(proxyOptions, proxyAddr, null, false);

        bootstrap.handler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addFirst(CHANNEL_HANDLER_PROXY, proxy);
            pipeline.addLast(CHANNEL_HANDLER_PROXY_CONNECTED, new ChannelInboundHandlerAdapter() {
              @Override
              public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof ProxyConnectionEvent) {
                  pipeline.remove(proxy);
                  pipeline.remove(this);
                  initSSL(handler, peerAddress, serverName, ssl, sslOptions, ch, channelHandler);
                  if (!ssl) {
                    connected(ch, channelHandler);
                  }
                }
                ctx.fireUserEventTriggered(evt);
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                channelHandler.tryFailure(cause);
              }
            });
          }
        });
        ChannelFuture future = bootstrap.connect(targetAddress);

        future.addListener(res -> {
          if (!res.isSuccess()) {
            channelHandler.tryFailure(res.cause());
          }
        });
      } else {
        channelHandler.tryFailure(dnsRes.cause());
      }
    });
  }

  public static class VertxConnectException extends ConnectException {
    public VertxConnectException(Throwable cause) {
      super(cause.getMessage());
      initCause(cause);
    }
  }
}
