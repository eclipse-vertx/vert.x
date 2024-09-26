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
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.util.concurrent.ImmediateExecutor;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.SocketAddress;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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

  public ChannelHandler createClientSslHandler(SocketAddress peerAddress, String serverName, boolean useAlpn,
                                               boolean http3,
                                               long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    SslContext sslContext = sslContextProvider.sslClientContext(serverName, useAlpn, http3);
    SslHandler sslHandler;
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    if (http3) {
      return Http3.newQuicClientCodecBuilder()
        .sslTaskExecutor(delegatedTaskExec)
        .sslContext((QuicSslContext) ((VertxSslContext) sslContext).unwrap())
        .maxIdleTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit)
        .initialMaxData(10000000) // Todo: Make this value configurable!
        .initialMaxStreamDataBidirectionalLocal(1000000) // Todo: Make this value configurable!
        .initialMaxStreamsBidirectional(100)
        .build();
    }
    if (peerAddress != null && peerAddress.isInetSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, peerAddress.host(), peerAddress.port(),
        delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public ChannelHandler createServerHandler(boolean useAlpn, boolean http3, long sslHandshakeTimeout,
                                            TimeUnit sslHandshakeTimeoutUnit, ChannelInitializer<QuicChannel> handler) {
    if (sni) {
      return createSniHandler(useAlpn, http3, sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    } else {
      return createServerSslHandler(useAlpn, http3, sslHandshakeTimeout, sslHandshakeTimeoutUnit, handler);
    }
  }

  private ChannelHandler createServerSslHandler(boolean useAlpn, boolean http3, long sslHandshakeTimeout,
                                                TimeUnit sslHandshakeTimeoutUnit,
                                                ChannelInitializer<QuicChannel> handler) {
    log.debug("Creating Server Ssl Handler ... ");
    SslContext sslContext = sslContextProvider.sslServerContext(useAlpn, http3);
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    if (http3) {
      log.debug("Creating HTTP/3 Server Ssl Handler ... ");
      Arguments.require(handler != null, "handler can't be null for http/3");

      // Todo: Make params configurable!
      return Http3.newQuicServerCodecBuilder()
        .sslContext((QuicSslContext) ((VertxSslContext) sslContext).unwrap())
        .maxIdleTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit)
        .initialMaxData(10000000)
        .initialMaxStreamDataBidirectionalLocal(1000000)
        .initialMaxStreamDataBidirectionalRemote(1000000)
        .initialMaxStreamsBidirectional(100)
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        .handler(handler)
        .build();
    }

    SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  private SniHandler createSniHandler(boolean useAlpn, boolean http3, long sslHandshakeTimeout,
                                      TimeUnit sslHandshakeTimeoutUnit) {
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(sslContextProvider.serverNameMapping(delegatedTaskExec, useAlpn, http3),
      sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout), delegatedTaskExec);
  }

}
