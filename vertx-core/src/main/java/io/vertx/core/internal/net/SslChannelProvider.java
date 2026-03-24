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
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.ImmediateExecutor;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.tls.ClientSslContextProvider;
import io.vertx.core.internal.tls.ServerSslContextProvider;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Provider for Netty {@link SslHandler} and {@link SniHandler}.
 * <br/>
 * {@link SslContext} instances are cached and reused.
 */
public class SslChannelProvider {

  private final Executor workerPool;
  private final boolean sni;
  private final SslContextProvider sslContextProvider;

  public SslChannelProvider(VertxInternal vertx,
                            SslContextProvider sslContextProvider,
                            boolean sni) {
    this.workerPool = vertx.internalWorkerPool().executor();
    this.sni = sni;
    this.sslContextProvider = sslContextProvider;
  }

  public SslContextProvider sslContextProvider() {
    return sslContextProvider;
  }

  public SslHandler createClientSslHandler(HostAndPort peer,
                                           String serverName,
                                           List<String> applicationProtocols,
                                           long sslHandshakeTimeout,
                                           TimeUnit sslHandshakeTimeoutUnit) {
    SslContext sslContext = ((ClientSslContextProvider)sslContextProvider).sslClientContext(serverName, applicationProtocols);
    SslHandler sslHandler;
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    if (peer != null) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, peer.host(), peer.port(), delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public ChannelHandler createServerHandler(List<String> applicationProtocols, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit, HostAndPort remoteAddress) {
    if (sni) {
      return createSniHandler(applicationProtocols, sslHandshakeTimeout, sslHandshakeTimeoutUnit, remoteAddress);
    } else {
      return createServerSslHandler(applicationProtocols, sslHandshakeTimeout, sslHandshakeTimeoutUnit, remoteAddress);
    }
  }

  private SslHandler createServerSslHandler(List<String> applicationProtocols, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit, HostAndPort remoteAddress) {
    SslContext sslContext = ((ServerSslContextProvider)sslContextProvider).sslServerContext(applicationProtocols);
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    SslHandler sslHandler;
    if (remoteAddress != null) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  private SniHandler createSniHandler(List<String> applicationProtocols, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit, HostAndPort remoteAddress) {
    Executor delegatedTaskExec = sslContextProvider.useWorkerPool() ? workerPool : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(((ServerSslContextProvider)sslContextProvider).serverNameAsyncMapping(delegatedTaskExec, applicationProtocols), sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout), delegatedTaskExec, remoteAddress);
  }

}
