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
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.quic.QuicSslContext;
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
  private final boolean http3;
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
                            boolean useWorkerPool,
                            boolean http3) {
    this.workerPool = workerPool;
    this.useWorkerPool = useWorkerPool;
    this.useAlpn = useAlpn;
    this.sni = sni;
    this.trustAll = trustAll;
    this.sslHandshakeTimeout = sslHandshakeTimeout;
    this.sslHandshakeTimeoutUnit = sslHandshakeTimeoutUnit;
    this.sslContextProvider = sslContextProvider;
    this.http3 = http3;
  }

  public int sniEntrySize() {
    return sslContextMaps[0].size() + sslContextMaps[1].size();
  }

  public SslContextProvider sslContextProvider() {
    return sslContextProvider;
  }

  public SslContext sslClientContext(String serverName, boolean useAlpn) {
    return sslClientContext(serverName, useAlpn, trustAll);
  }

  public SslContext sslClientContext(String serverName, boolean useAlpn, boolean trustAll) {
    try {
      return sslContext(serverName, useAlpn, false, trustAll);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslContext sslContext(String serverName, boolean useAlpn, boolean server, boolean trustAll) throws Exception {
    int idx = idx(useAlpn);
    if (serverName != null) {
      KeyManagerFactory kmf = sslContextProvider.resolveKeyManagerFactory(serverName);
      TrustManager[] trustManagers = trustAll ? null : sslContextProvider.resolveTrustManagers(serverName);
      if (kmf != null || trustManagers != null || !server) {
        return sslContextMaps[idx].computeIfAbsent(serverName, s -> sslContextProvider.createContext(server, kmf,
          trustManagers, s, useAlpn, trustAll, http3));
      }
    }
    if (sslContexts[idx] == null) {
      SslContext context = sslContextProvider.createContext(server, null, null, serverName, useAlpn, trustAll, http3);
      sslContexts[idx] = context;
    }
    return sslContexts[idx];
  }

  public SslContext sslServerContext(boolean useAlpn) {
    try {
      return sslContext(null, useAlpn, true, false);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  /**
   * Server name {@link AsyncMapping} for {@link SniHandler}, mapping happens on a Vert.x worker thread.
   *
   * @return the {@link AsyncMapping}
   */
  public AsyncMapping<? super String, ? extends SslContext> serverNameMapping() {
    return (AsyncMapping<String, SslContext>) (serverName, promise) -> {
      workerPool.execute(() -> {
        SslContext sslContext;
        try {
          sslContext = sslContext(serverName, useAlpn, true, false);
        } catch (Exception e) {
          promise.setFailure(e);
          return;
        }
        promise.setSuccess(sslContext);
      });
      return promise;
    };
  }

  public ChannelHandler createClientSslHandler(SocketAddress remoteAddress, String serverName, boolean useAlpn) {
    SslContext sslContext = sslClientContext(serverName, useAlpn);
    ChannelHandler sslHandler;
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    if (http3) {
      sslHandler = Http3.newQuicClientCodecBuilder()
        .sslTaskExecutor(delegatedTaskExec)
        .sslContext((QuicSslContext) ((VertxSslContext) sslContext).unwrap())
        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
        .initialMaxData(10000000)
        .initialMaxStreamDataBidirectionalLocal(1000000)
        .build();
    } else {
      SslHandler sslHandler0;
      if (remoteAddress.isDomainSocket()) {
        sslHandler0 = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
      } else {
        sslHandler0 = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(),
          delegatedTaskExec);
      }
      sslHandler0.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
      sslHandler = sslHandler0;
    }
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
