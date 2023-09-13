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
package io.vertx.core.net.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsyncMapping;
import io.netty.util.concurrent.ImmediateExecutor;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Provider for {@link SslHandler} and {@link SniHandler}.
 * <br/>
 * {@link SslContext} instances are cached and reused.
 */
public class SslChannelProvider {

  private final Executor workerPool;
  private final boolean useWorkerPool;
  private final boolean sni;
  private final SslContextProvider sslContextProvider;
  private final SslContext[] sslContexts = new SslContext[2];
  private final Map<String, SslContext>[] sslContextMaps = new Map[]{
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };

  public SslChannelProvider(SslContextProvider sslContextProvider,
                            boolean sni,
                            Executor workerPool,
                            boolean useWorkerPool) {
    this.workerPool = workerPool;
    this.useWorkerPool = useWorkerPool;
    this.sni = sni;
    this.sslContextProvider = sslContextProvider;
  }

  public SslContextProvider sslContextProvider() {
    return sslContextProvider;
  }

  public SslContext sslClientContext(String serverName, boolean useAlpn, boolean trustAll) {
    int idx = idx(useAlpn);
    if (sslContexts[idx] == null) {
      SslContext context = sslContextProvider.createClientContext(serverName, useAlpn, trustAll);
      sslContexts[idx] = context;
    }
    return sslContexts[idx];
  }

  public SslContext sslServerContext(boolean useAlpn) {
    int idx = idx(useAlpn);
    if (sslContexts[idx] == null) {
      sslContexts[idx] = sslContextProvider.createServerContext(useAlpn);
    }
    return sslContexts[idx];
  }

  /**
   * Server name {@link AsyncMapping} for {@link SniHandler}, mapping happens on a Vert.x worker thread.
   *
   * @return the {@link AsyncMapping}
   */
  public AsyncMapping<? super String, ? extends SslContext> serverNameMapping(boolean useAlpn) {
    return (AsyncMapping<String, SslContext>) (serverName, promise) -> {
      workerPool.execute(() -> {
        if (serverName == null) {
          promise.setSuccess(sslServerContext(useAlpn));
        } else {
          KeyManagerFactory kmf;
          try {
            kmf = sslContextProvider.resolveKeyManagerFactory(serverName);
          } catch (Exception e) {
            promise.setFailure(e);
            return;
          }
          TrustManager[] trustManagers;
          try {
            trustManagers = sslContextProvider.resolveTrustManagers(serverName);
          } catch (Exception e) {
            promise.setFailure(e);
            return;
          }
          int idx = idx(useAlpn);
          SslContext sslContext = sslContextMaps[idx].computeIfAbsent(serverName, s -> sslContextProvider.createServerContext(kmf, trustManagers, s, useAlpn));
          promise.setSuccess(sslContext);
        }
      });
      return promise;
    };
  }

  public SslHandler createClientSslHandler(SocketAddress peerAddress, String serverName, boolean useAlpn, boolean trustAll, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    SslContext sslContext = sslClientContext(serverName, useAlpn, trustAll);
    SslHandler sslHandler;
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    if (peerAddress != null && peerAddress.isInetSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, peerAddress.host(), peerAddress.port(), delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public ChannelHandler createServerHandler(boolean useAlpn, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    if (sni) {
      return createSniHandler(useAlpn, sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    } else {
      return createServerSslHandler(useAlpn, sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    }
  }

  private SslHandler createServerSslHandler(boolean useAlpn, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    SslContext sslContext = sslServerContext(useAlpn);
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  private SniHandler createSniHandler(boolean useAlpn, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(serverNameMapping(useAlpn), sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout), delegatedTaskExec);
  }

  private static int idx(boolean useAlpn) {
    return useAlpn ? 0 : 1;
  }
}
