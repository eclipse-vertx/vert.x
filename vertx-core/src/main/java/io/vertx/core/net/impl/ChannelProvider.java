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
import io.netty.channel.*;
import io.netty.handler.proxy.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLHandshakeException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The logic for connecting to an host, this implementations performs a connection
 * to the host after resolving its internet address.
 *
 * See if we can replace that by a Netty handler sometimes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class ChannelProvider {

  public static final String SSL_CHANNEL_NAME = "ssl";
  public static final ChannelInitializer<Channel> EMPTY_HANDLER = new ChannelInitializer<>() {
    @Override
    protected void initChannel(Channel channel) {
    }
  };
  private final Bootstrap bootstrap;
  private final SslChannelProvider sslContextProvider;
  private final ContextInternal context;
  private ProxyOptions proxyOptions;
  private String applicationProtocol;
  private Handler<Channel> handler;
  private HttpVersion version;

  public ChannelProvider(Bootstrap bootstrap,
                         SslChannelProvider sslContextProvider,
                         ContextInternal context) {
    this.bootstrap = bootstrap;
    this.context = context;
    this.sslContextProvider = sslContextProvider;
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

  public ChannelProvider version(HttpVersion version) {
    this.version = version;
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

  /**
   * @return the application protocol resulting from the ALPN negotiation
   */
  public String applicationProtocol() {
    return applicationProtocol;
  }

  public Future<Channel> connect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions) {
    Promise<Channel> p = context.nettyEventLoop().newPromise();
    connect(handler, remoteAddress, peerAddress, serverName, ssl, sslOptions, p);
    return p;
  }

  private void connect(Handler<Channel> handler, SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, Promise<Channel> p) {
    try {
      if (version == HttpVersion.HTTP_3) {
        bootstrap.channelFactory(() -> context.owner().transport().datagramChannel());
      } else {
        bootstrap.channelFactory(context.owner().transport().channelFactory(remoteAddress.isDomainSocket()));
      }
    } catch (Exception e) {
      p.setFailure(e);
      return;
    }
    if (proxyOptions != null) {
      handleProxyConnect(handler, remoteAddress, peerAddress, serverName, ssl, sslOptions, p);
    } else {
      handleConnect(handler, remoteAddress, peerAddress, serverName, ssl, sslOptions, p);
    }
  }

  private void initSSL(Handler<Channel> handler, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, Channel ch, Promise<Channel> channelHandler) {
    if (ssl) {
      ChannelHandler sslHandler = sslContextProvider.createClientSslHandler(peerAddress, serverName,
        sslOptions.isUseAlpn(), sslOptions.isHttp3(), sslOptions.getSslHandshakeTimeout(),
        sslOptions.getSslHandshakeTimeoutUnit());
      //todo: correct the following code:
//      ChannelPipeline pipeline = version == HttpVersion.HTTP_3 ? ch.parent().pipeline() : ch.pipeline();
      ChannelPipeline pipeline = ch.pipeline();
      pipeline.addLast(SSL_CHANNEL_NAME, sslHandler);
      pipeline.addLast(new ChannelInboundHandlerAdapter() {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
          if (evt instanceof SslHandshakeCompletionEvent) {
            // Notify application
            SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
            if (completion.isSuccess()) {
              // Remove from the pipeline after handshake result
              ctx.pipeline().remove(this);
              applicationProtocol = ((SslHandler)sslHandler).applicationProtocol();
              if (handler != null) {
                context.dispatch(ch, handler);
              }
              channelHandler.setSuccess(ctx.channel());
            } else {
              SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
              sslException.initCause(completion.cause());
              channelHandler.setFailure(sslException);
            }
          }
          ctx.fireUserEventTriggered(evt);
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
          // Ignore these exception as they will be reported to the handler
        }
      });
    }
  }


  private void handleConnect(Handler<Channel> handler, SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, Promise<Channel> channelHandler) {
    VertxInternal vertx = context.owner();
    bootstrap.resolver(vertx.nettyAddressResolverGroup());
    bootstrap.handler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        initSSL(handler, peerAddress, serverName, ssl, sslOptions, ch, channelHandler);
      }
    });
    ChannelFuture fut = bootstrap.connect(vertx.transport().convert(remoteAddress));
    fut.addListener(res -> {
      if (!res.isSuccess()) {
        channelHandler.setFailure(res.cause());
        return;
      }
      if (version != HttpVersion.HTTP_3) {
        connected(handler, fut.channel(), ssl, channelHandler);
        return;
      }
      Channel nioDatagramChannel = fut.channel();

      QuicChannel.newBootstrap(nioDatagramChannel)
        .handler(EMPTY_HANDLER)
        .localAddress(nioDatagramChannel.localAddress())
        .remoteAddress(nioDatagramChannel.remoteAddress())
        .connect()
        .addListener((io.netty.util.concurrent.Future<QuicChannel> future) -> {
          if (!future.isSuccess()) {
            channelHandler.setFailure(future.cause());
            return;
          }

          QuicChannel quicChannel = future.get();
          connected(handler, quicChannel, ssl, channelHandler);
        });
    });
  }

  /**
   * Signal we are connected to the remote server.
   *
   * @param channel        the channel
   * @param channelHandler the channel handler
   */
  private void connected(Handler<Channel> handler, Channel channel, boolean ssl, Promise<Channel> channelHandler) {
    if (version == HttpVersion.HTTP_3) {
      applicationProtocol = HttpVersion.HTTP_3.alpnName();
      if (handler != null) {
        context.dispatch(channel, handler);
      }
      channelHandler.setSuccess(channel);
    } else if (!ssl) {
      // No handshake
      if (handler != null) {
        context.dispatch(channel, handler);
      }
      channelHandler.setSuccess(channel);
    }
  }

  /**
   * A channel provider that connects via a Proxy : HTTP or SOCKS
   */
  private void handleProxyConnect(Handler<Channel> handler, SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, ClientSSLOptions sslOptions, Promise<Channel> channelHandler) {

    final VertxInternal vertx = context.owner();
    final String proxyHost = proxyOptions.getHost();
    final int proxyPort = proxyOptions.getPort();
    final String proxyUsername = proxyOptions.getUsername();
    final String proxyPassword = proxyOptions.getPassword();
    final ProxyType proxyType = proxyOptions.getType();

    vertx.resolveAddress(proxyHost).onComplete(dnsRes -> {
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
        java.net.SocketAddress targetAddress = vertx.transport().convert(remoteAddress);

        bootstrap.handler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addFirst("proxy", proxy);
            pipeline.addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof ProxyConnectionEvent) {
                  pipeline.remove(proxy);
                  pipeline.remove(this);
                  initSSL(handler, peerAddress, serverName, ssl, sslOptions, ch, channelHandler);
                  connected(handler, ch, ssl, channelHandler);
                }
                ctx.fireUserEventTriggered(evt);
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                channelHandler.setFailure(cause);
              }
            });
          }
        });
        ChannelFuture future = bootstrap.connect(targetAddress);

        future.addListener(res -> {
          if (!res.isSuccess()) {
            channelHandler.setFailure(res.cause());
          }
        });
      } else {
        channelHandler.setFailure(dnsRes.cause());
      }
    });
  }
}
