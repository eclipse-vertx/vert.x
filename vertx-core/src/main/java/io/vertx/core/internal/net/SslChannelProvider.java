/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.net;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.util.concurrent.ImmediateExecutor;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

/**
 * Provider for Netty {@link SslHandler} and {@link SniHandler}.
 * <br/>
 * {@link SslContext} instances are cached and reused.
 */
public class SslChannelProvider {
  private static final Logger log = LoggerFactory.getLogger(SslChannelProvider.class);

  private final Executor workerPool;
  private final boolean sni;
  private final SslContextProvider sslContextProvider;

  public SslChannelProvider(VertxInternal vertx,
                            SslContextProvider sslContextProvider,
                            boolean sni) {
    this.workerPool = vertx.getInternalWorkerPool().executor();
    this.sni = sni;
    this.sslContextProvider = sslContextProvider;
  }

  public SslContextProvider sslContextProvider() {
    return sslContextProvider;
  }

  public ChannelHandler createClientSslHandler(SocketAddress peerAddress, String serverName, SSLOptions sslOptions) {
    SslContext sslContext = sslContextProvider.sslClientContext(serverName, sslOptions.isUseAlpn(),
      sslOptions.isHttp3());
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    if (sslOptions.isHttp3()) {
      QuicSslContext quicSslContext = (QuicSslContext) ((VertxSslContext) sslContext).unwrap();
      return configureQuicCodecBuilder(Http3.newQuicClientCodecBuilder(), sslOptions, delegatedTaskExec)
        .sslEngineProvider(channel -> quicSslContext.newEngine(channel.alloc(), peerAddress.host(), peerAddress.port()))
        .build();
    }
    SslHandler sslHandler;
    if (peerAddress != null && peerAddress.isInetSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, peerAddress.host(), peerAddress.port(),
        delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslOptions.getSslHandshakeTimeout(), sslOptions.getSslHandshakeTimeoutUnit());
    return sslHandler;
  }

  public ChannelHandler createServerHandler(SSLOptions sslOptions, HostAndPort remoteAddress,
                                            ChannelInitializer<QuicChannel> handler) {
    if (sni) {
      return createSniHandler(sslOptions, remoteAddress, handler);
    } else {
      return createServerSslHandler(sslOptions, remoteAddress, handler);
    }
  }

  private ChannelHandler createServerSslHandler(SSLOptions sslOptions, HostAndPort remoteAddress,
                                                ChannelInitializer<QuicChannel> handler) {
    log.debug("Creating Server Ssl Handler ... ");
    SslContext sslContext = sslContextProvider.sslServerContext(sslOptions.isUseAlpn(), sslOptions.isHttp3());
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    if (sslOptions.isHttp3()) {
      log.debug("Creating HTTP/3 Server Ssl Handler ... ");
      Arguments.require(handler != null, "handler can't be null for http/3");

      return configureQuicCodecBuilder(Http3.newQuicServerCodecBuilder(), sslOptions,
        delegatedTaskExec)
        .sslContext((QuicSslContext) ((VertxSslContext) sslContext).unwrap())
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        .handler(handler)
        .build();
    }

    SslHandler sslHandler;
    if (remoteAddress != null) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(),
        delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }

    sslHandler.setHandshakeTimeout(sslOptions.getSslHandshakeTimeout(), sslOptions.getSslHandshakeTimeoutUnit());
    return sslHandler;
  }

  private ChannelHandler createSniHandler(SSLOptions sslOptions, HostAndPort remoteAddress,
                                          ChannelInitializer<QuicChannel> handler) {
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    if (sslOptions.isHttp3()) {
      QuicSslContext serverSslContextWithSni =
        QuicSslContextBuilder.buildForServerWithSni(sslContextProvider.serverNameMapping(sslOptions.isUseAlpn(),
        sslOptions.isHttp3()));

      return configureQuicCodecBuilder(Http3.newQuicServerCodecBuilder(), sslOptions,
        delegatedTaskExec)
        .sslEngineProvider(quicChannel -> {
          InetSocketAddress address = ((InetSocketAddress) quicChannel.remoteSocketAddress());
          return serverSslContextWithSni.newEngine(quicChannel.alloc(), address.getHostString(), address.getPort());
        })
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        .handler(handler)
        .build();
    }

    return new VertxSniHandler(sslContextProvider.serverNameMapping(delegatedTaskExec, sslOptions.isUseAlpn(),
      sslOptions.isHttp3()), sslOptions.getSslHandshakeTimeoutUnit().toMillis(sslOptions.getSslHandshakeTimeout()),
      delegatedTaskExec, remoteAddress);
  }

  private static <T extends QuicCodecBuilder<T>> T configureQuicCodecBuilder(T quicCodecBuilder, SSLOptions sslOptions,
                                                                      Executor delegatedTaskExec) {
    quicCodecBuilder
      .sslTaskExecutor(delegatedTaskExec)
      .maxIdleTimeout(sslOptions.getSslHandshakeTimeout(), sslOptions.getSslHandshakeTimeoutUnit())
      .initialMaxData(sslOptions.getHttp3InitialMaxData())
      .initialMaxStreamsBidirectional(sslOptions.getHttp3InitialMaxStreamsBidirectional())
      .initialMaxStreamDataBidirectionalLocal(sslOptions.getHttp3InitialMaxStreamDataBidirectionalLocal())
      .initialMaxStreamDataBidirectionalRemote(sslOptions.getHttp3InitialMaxStreamDataBidirectionalRemote())
      .initialMaxStreamsUnidirectional(sslOptions.getHttp3InitialMaxStreamsUnidirectional())
      .initialMaxStreamDataUnidirectional(sslOptions.getHttp3InitialMaxStreamDataUnidirectional())
    ;
    return quicCodecBuilder;
  }

}
