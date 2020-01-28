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
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
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

  private final Bootstrap bootstrap;
  private final SSLHelper sslHelper;
  private final ContextInternal context;
  private final ProxyOptions proxyOptions;
  private String applicationProtocol;
  private Channel channel;

  public ChannelProvider(Bootstrap bootstrap,
                         SSLHelper sslHelper,
                         ContextInternal context,
                         ProxyOptions proxyOptions) {
    this.bootstrap = bootstrap;
    this.context = context;
    this.sslHelper = sslHelper;
    this.proxyOptions = proxyOptions;
  }

  /**
   * @return the application protocol resulting from the ALPN negotiation
   */
  public String applicationProtocol() {
    return applicationProtocol;
  }

  /**
   * @return the created channel
   */
  public Channel channel() {
    return channel;
  }

  public Future<Channel> connect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl) {
    Promise<Channel> p = context.nettyEventLoop().newPromise();
    connect(remoteAddress, peerAddress, serverName, ssl, p);
    return p;
  }

  private void connect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, Promise<Channel> p) {
    try {
      bootstrap.channelFactory(context.owner().transport().channelFactory(remoteAddress.isDomainSocket()));
    } catch (Exception e) {
      p.setFailure(e);
      return;
    }
    if (proxyOptions != null) {
      handleProxyConnect(remoteAddress, peerAddress, serverName, ssl, p);
    } else {
      handleConnect(remoteAddress, peerAddress, serverName, ssl, p);
    }
  }

  private void initSSL(SocketAddress peerAddress, String serverName, boolean ssl, Channel ch, Promise<Channel> channelHandler) {
    if (ssl) {
      SslHandler sslHandler = new SslHandler(sslHelper.createEngine(context.owner(), peerAddress, serverName));
      sslHandler.setHandshakeTimeout(sslHelper.getSslHandshakeTimeout(), sslHelper.getSslHandshakeTimeoutUnit());
      ChannelPipeline pipeline = ch.pipeline();
      pipeline.addLast("ssl", sslHandler);
      pipeline.addLast(new ChannelInboundHandlerAdapter() {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
          if (evt instanceof SslHandshakeCompletionEvent) {
            // Notify application
            SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
            if (completion.isSuccess()) {
              // Remove from the pipeline after handshake result
              ctx.pipeline().remove(this);
              applicationProtocol = sslHandler.applicationProtocol();
              channelHandler.setSuccess(channel);
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


  private void handleConnect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, Promise<Channel> channelHandler) {
    VertxInternal vertx = context.owner();
    bootstrap.resolver(vertx.nettyAddressResolverGroup());
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) {
        initSSL(peerAddress, serverName, ssl, ch, channelHandler);
      }
    });
    ChannelFuture fut = bootstrap.connect(vertx.transport().convert(remoteAddress));
    fut.addListener(res -> {
      if (res.isSuccess()) {
        connected(fut.channel(), ssl, channelHandler);
      } else {
        channelHandler.setFailure(res.cause());
      }
    });
  }

  /**
   * Signal we are connected to the remote server.
   *
   * @param channel the channel
   * @param channelHandler the channel handler
   */
  private void connected(Channel channel, boolean ssl, Promise<Channel> channelHandler) {
    this.channel = channel;
    if (!ssl) {
      // No handshake
      channelHandler.setSuccess(channel);
    }
  }

  /**
   * A channel provider that connects via a Proxy : HTTP or SOCKS
   */
  private void handleProxyConnect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, Promise<Channel> channelHandler) {

    final VertxInternal vertx = context.owner();
    final String proxyHost = proxyOptions.getHost();
    final int proxyPort = proxyOptions.getPort();
    final String proxyUsername = proxyOptions.getUsername();
    final String proxyPassword = proxyOptions.getPassword();
    final ProxyType proxyType = proxyOptions.getType();

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
                  initSSL(peerAddress, serverName, ssl, ch, channelHandler);
                  connected(ch, ssl, channelHandler);
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
