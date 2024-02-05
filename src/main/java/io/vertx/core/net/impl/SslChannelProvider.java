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
import io.vertx.core.VertxException;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.net.impl.SslContextProvider.createTrustAllTrustManager;

/**
 * Provider for {@link SslHandler} and {@link SniHandler}.
 * <br/>
 * {@link SslContext} instances are cached and reused.
 */
public class SslChannelProvider {

  private final long sslHandshakeTimeout;
  private final TimeUnit sslHandshakeTimeoutUnit;
  private final Executor workerPool;
  private final boolean useWorkerPool;
  private final boolean sni;
  private final boolean useAlpn;
  private final boolean trustAll;
  private final SslContextProvider sslContextProvider;
  private final SslContext[] sslContexts = new SslContext[2];
  private final Map<String, SslContext>[] sslContextMaps = new Map[]{
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };

  public SslChannelProvider(SslContextProvider sslContextProvider,
                            long sslHandshakeTimeout,
                            TimeUnit sslHandshakeTimeoutUnit,
                            boolean sni,
                            boolean trustAll,
                            boolean useAlpn,
                            Executor workerPool,
                            boolean useWorkerPool) {
    this.workerPool = workerPool;
    this.useWorkerPool = useWorkerPool;
    this.useAlpn = useAlpn;
    this.sni = sni;
    this.trustAll = trustAll;
    this.sslHandshakeTimeout = sslHandshakeTimeout;
    this.sslHandshakeTimeoutUnit = sslHandshakeTimeoutUnit;
    this.sslContextProvider = sslContextProvider;
  }

  public SslContextProvider sslContextProvider() {
    return sslContextProvider;
  }

  public SslContext sslClientContext(String serverName, boolean useAlpn) {
    return sslClientContext(serverName, useAlpn, trustAll);
  }

  public SslContext sslClientContext(String serverName, boolean useAlpn, boolean trustAll) {
    int idx = idx(useAlpn);
    if (serverName == null) {
      if (sslContexts[idx] == null) {
        SslContext context = sslContextProvider.createClientContext(useAlpn, trustAll);
        sslContexts[idx] = context;
      }
      return sslContexts[idx];
    } else {
      KeyManagerFactory kmf;
      try {
        kmf = sslContextProvider.resolveKeyManagerFactory(serverName);
      } catch (Exception e) {
        throw new VertxException(e);
      }
      TrustManager[] trustManagers;
      if (trustAll) {
        trustManagers = new TrustManager[] { createTrustAllTrustManager() };
      } else {
        try {
          trustManagers = sslContextProvider.resolveTrustManagers(serverName);
        } catch (Exception e) {
          throw new VertxException(e);
        }
      }
      return sslContextMaps[idx].computeIfAbsent(serverName, s -> sslContextProvider.createClientContext(kmf, trustManagers, s, useAlpn));
    }
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
  public AsyncMapping<? super String, ? extends SslContext> serverNameMapping() {
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

  public SslHandler createClientSslHandler(SocketAddress remoteAddress, String serverName, boolean useAlpn) {
    SslContext sslContext = sslClientContext(serverName, useAlpn);
    SslHandler sslHandler;
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    if (remoteAddress.isDomainSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public ChannelHandler createServerHandler() {
    if (sni) {
      return createSniHandler();
    } else {
      return createServerSslHandler(useAlpn);
    }
  }

  private SslHandler createServerSslHandler(boolean useAlpn) {
    SslContext sslContext = sslServerContext(useAlpn);
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  private SniHandler createSniHandler() {
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(serverNameMapping(), sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout), delegatedTaskExec);
  }

  private static int idx(boolean useAlpn) {
    return useAlpn ? 0 : 1;
  }
}
