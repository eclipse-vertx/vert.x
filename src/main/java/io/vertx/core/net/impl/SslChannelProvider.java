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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.internal.tcnative.SSL;
import io.netty.util.AsyncMapping;
import io.netty.util.concurrent.ImmediateExecutor;
import io.vertx.core.VertxException;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Provider for {@link SslHandler} and {@link SniHandler}.
 * <br/>
 * {@link SslContext} instances are cached and reused.
 */
public class SslChannelProvider {

  private static final Logger log = LoggerFactory.getLogger(SslChannelProvider.class);
  private final long sslHandshakeTimeout;
  private final TimeUnit sslHandshakeTimeoutUnit;
  private final Executor workerPool;
  private final boolean useWorkerPool;
  private final boolean sni;
  private final boolean useAlpn;
  private final boolean useHybrid;
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
                            boolean useWorkerPool, boolean useHybrid) {
    this.workerPool = workerPool;
    this.useWorkerPool = useWorkerPool;
    this.useAlpn = useAlpn;
    this.sni = sni;
    this.trustAll = trustAll;
    this.sslHandshakeTimeout = sslHandshakeTimeout;
    this.sslHandshakeTimeoutUnit = sslHandshakeTimeoutUnit;
    this.sslContextProvider = sslContextProvider;
    this.useHybrid = useHybrid;
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
        return sslContextMaps[idx].computeIfAbsent(serverName, s -> sslContextProvider.createContext(server, kmf, trustManagers, s, useAlpn, trustAll));
      }
    }
    if (sslContexts[idx] == null) {
      SslContext context = sslContextProvider.createContext(server, null, null, serverName, useAlpn, trustAll);
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

  public SslHandler createClientSslHandler(SocketAddress remoteAddress, String serverName, boolean useAlpn) throws Exception {
    SslContext sslContext = sslClientContext(serverName, useAlpn);
    SslHandler sslHandler;
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    if (remoteAddress.isDomainSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    }
    if (useHybrid) {
      SSLEngine engine = sslHandler.engine();
      try {
        long sslPtr = ((ReferenceCountedOpenSslEngine) engine).sslPointer();
        boolean success = SSL.setCurvesList(sslPtr, "X25519MLKEM768");
        if (!success) {
          throw new Exception("Failed to set hybrid PQC groups on SSL instance");
        }
      } catch (Exception e) {
        throw new Exception("Unable to create sslHandler: "+e.getMessage());
      }
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public ChannelHandler createServerHandler(HostAndPort remoteAddress) throws Exception {
    if (sni) {
      SniHandler sniHandler = createSniHandler(useHybrid, remoteAddress);
      if(sniHandler == null){
        throw new Exception("Unable to create a SNI handler");
      }
      return sniHandler;
    } else {
      return createServerSslHandler(useAlpn, useHybrid, remoteAddress);
    }
  }

  private SslHandler createServerSslHandler(boolean useAlpn, boolean useHybrid, HostAndPort remoteAddress) throws Exception {
    SslContext sslContext = sslServerContext(useAlpn);
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    SslHandler sslHandler;
    if (remoteAddress != null) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    }
    if (useHybrid) {
      SSLEngine engine = sslHandler.engine();
      try {
        long sslPtr = ((ReferenceCountedOpenSslEngine) engine).sslPointer();
        boolean success = SSL.setCurvesList(sslPtr, "X25519MLKEM768");
        if (!success) {
          throw new Exception("Failed to set hybrid PQC groups on SSL instance");
        }
      } catch (Exception e) {
        throw new Exception("Unable to create sslHandler: "+e.getMessage());
      }
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  private SniHandler createSniHandler(boolean useHybrid, HostAndPort remoteAddress) {
    Executor delegatedTaskExec = useWorkerPool ? workerPool : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(serverNameMapping(), sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout), delegatedTaskExec,
      useHybrid, remoteAddress);
  }

  private static int idx(boolean useAlpn) {
    return useAlpn ? 0 : 1;
  }
}
