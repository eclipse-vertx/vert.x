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
import io.netty.channel.*;
import io.netty.handler.proxy.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.resolver.NoopAddressResolverGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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

  public void connect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, Handler<AsyncResult<Channel>> channelHandler) {
    Handler<AsyncResult<Channel>> handler = res -> {
      if (Context.isOnEventLoopThread()) {
        channelHandler.handle(res);
      } else {
        // We are on the GlobalEventExecutor
        context.nettyEventLoop().execute(() -> channelHandler.handle(res));
      }
    };
    if (proxyOptions != null) {
      handleProxyConnect(remoteAddress, peerAddress, serverName, ssl, handler);
    } else {
      handleConnect(remoteAddress, peerAddress, serverName, ssl, handler);
    }
  }

  private void initSSL(SocketAddress peerAddress, String serverName, boolean ssl, Channel ch, Handler<AsyncResult<Channel>> channelHandler) {
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
              channelHandler.handle(io.vertx.core.Future.succeededFuture(channel));
            } else {
              SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
              sslException.initCause(completion.cause());
              channelHandler.handle(io.vertx.core.Future.failedFuture(sslException));
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


  private void handleConnect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, Handler<AsyncResult<Channel>> channelHandler) {
    VertxInternal vertx = context.owner();
    bootstrap.resolver(vertx.nettyAddressResolverGroup());
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) {
        initSSL(peerAddress, serverName, ssl, ch, channelHandler);
      }
    });
    ChannelFuture fut = bootstrap.connect(vertx.transport().convert(remoteAddress, false));
    fut.addListener(res -> {
      if (res.isSuccess()) {
        connected(fut.channel(), ssl, channelHandler);
      } else {
        channelHandler.handle(io.vertx.core.Future.failedFuture(res.cause()));
      }
    });
  }

  /**
   * Signal we are connected to the remote server.
   *
   * @param channel the channel
   * @param channelHandler the channel handler
   */
  private void connected(Channel channel, boolean ssl, Handler<AsyncResult<Channel>> channelHandler) {
    this.channel = channel;
    if (!ssl) {
      // No handshake
      channelHandler.handle(io.vertx.core.Future.succeededFuture(this.channel));
    }
  }

  /**
   * A channel provider that connects via a Proxy : HTTP or SOCKS
   */
  private void handleProxyConnect(SocketAddress remoteAddress, SocketAddress peerAddress, String serverName, boolean ssl, Handler<AsyncResult<Channel>> channelHandler) {

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
        java.net.SocketAddress targetAddress = vertx.transport().convert(remoteAddress, false);

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
