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
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsyncMapping;
import io.netty.util.Mapping;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.concurrent.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Provides Netty's {@link SslContext}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslContextProvider {

  private final SSLHelper sslHelper;
  private final Supplier<SslContextFactory> provider;
  private final long sslHandshakeTimeout;
  private final TimeUnit sslHandshakeTimeoutUnit;
  final Set<String> enabledProtocols;
  private final KeyCertOptions keyCertOptions;
  private final TrustOptions trustOptions;
  private final ArrayList<String> crlPaths;
  private final ArrayList<Buffer> crlValues;
  private final Set<String> enabledCipherSuites;
  private final SslContext[] sslContexts = new SslContext[2];
  private final Map<String, SslContext>[] sslContextMaps = new Map[]{
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };
  private final boolean useWorkerPool;

  public SslContextProvider(SSLHelper sslHelper, SSLOptions options, boolean useWorkerPool, Supplier<SslContextFactory> provider) {
    this.sslHelper = sslHelper;
    this.provider = provider;
    this.crlPaths = new ArrayList<>(options.getCrlPaths());
    this.crlValues = new ArrayList<>(options.getCrlValues());
    this.enabledCipherSuites = new HashSet<>(options.getEnabledCipherSuites());
    this.sslHandshakeTimeout = options.getSslHandshakeTimeout();
    this.sslHandshakeTimeoutUnit = options.getSslHandshakeTimeoutUnit();
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
    this.keyCertOptions = options.getKeyCertOptions();
    this.trustOptions = options.getTrustOptions();
    this.useWorkerPool = useWorkerPool;
  }

  public boolean isSsl() {
    return sslHelper.isSSL();
  }

  public SslContext createContext(VertxInternal vertx, String serverName, boolean useAlpn, boolean client, boolean trustAll) {
    int idx = useAlpn ? 0 : 1;
    if (serverName == null) {
      if (sslContexts[idx] == null) {
        sslContexts[idx] = createContext2(vertx, serverName, useAlpn, client, trustAll);
      }
      return sslContexts[idx];
    } else {
      return sslContextMaps[idx].computeIfAbsent(serverName, s -> createContext2(vertx, serverName, useAlpn, client, trustAll));
    }
  }

  private SslContext createContext2(VertxInternal vertx, String serverName, boolean useAlpn, boolean client, boolean trustAll) {
    try {
      TrustManagerFactory tmf = SSLHelper.getTrustMgrFactory(vertx, trustOptions, crlPaths, crlValues, serverName, trustAll);
      KeyManagerFactory kmf = SSLHelper.getKeyMgrFactory(vertx, keyCertOptions, serverName);
      SslContextFactory factory = provider.get()
        .useAlpn(useAlpn)
        .forClient(client)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(sslHelper.applicationProtocols);
      if (!client) {
        factory.clientAuth(SSLHelper.CLIENT_AUTH_MAPPING.get(sslHelper.clientAuth));
      }
      if (kmf != null) {
        factory.keyMananagerFactory(kmf);
      }
      if (tmf != null) {
        factory.trustManagerFactory(tmf);
      }
      if (serverName != null) {
        factory.serverName(serverName);
      }
      return factory.create();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslContext createContext(VertxInternal vertx) {
    return createContext(vertx, null, sslHelper.useAlpn, sslHelper.client, sslHelper.trustAll);
  }

  public SslContext sslContext(VertxInternal vertx, String serverName, boolean useAlpn) {
    SslContext context = createContext(vertx, null, useAlpn, sslHelper.client, sslHelper.trustAll);
    return new DelegatingSslContext(context) {
      @Override
      protected void initEngine(SSLEngine engine) {
        sslHelper.configureEngine(engine, enabledProtocols, serverName);
      }
    };
  }

  public AsyncMapping<? super String, ? extends SslContext> serverNameMapper(VertxInternal vertx, ContextInternal ctx) {
    return (AsyncMapping<String, SslContext>) (serverName, promise) -> {
      ctx.<SslContext>executeBlockingInternal(p -> {
        SslContext sslContext = createContext(vertx, serverName, sslHelper.useAlpn, sslHelper.client, sslHelper.trustAll);
        if (sslContext != null) {
          sslContext = new DelegatingSslContext(sslContext) {
            @Override
            protected void initEngine(SSLEngine engine) {
              sslHelper.configureEngine(engine, enabledProtocols, serverName);
            }
          };
        }
        p.complete(sslContext);
      }, ar -> {
        if (ar.succeeded()) {
          promise.setSuccess(ar.result());
        } else {
          promise.setFailure(ar.cause());
        }
      });
      return promise;
    };
  }

  public SSLEngine createEngine(VertxInternal vertx) {
    SSLEngine engine = createContext(vertx).newEngine(ByteBufAllocator.DEFAULT);
    sslHelper.configureEngine(engine, enabledProtocols, null);
    return engine;
  }

  public SslHandler createSslHandler(VertxInternal vertx, String serverName) {
    return createSslHandler(vertx, null, serverName);
  }

  public SslHandler createSslHandler(VertxInternal vertx, SocketAddress remoteAddress, String serverName) {
    return createSslHandler(vertx, remoteAddress, serverName, sslHelper.useAlpn);
  }

  public SslHandler createSslHandler(VertxInternal vertx, SocketAddress remoteAddress, String serverName, boolean useAlpn) {
    SslContext sslContext = sslContext(vertx, serverName, useAlpn);
    SslHandler sslHandler;
    Executor delegatedTaskExec = useWorkerPool ? vertx.getInternalWorkerPool().executor() : ImmediateExecutor.INSTANCE;
    if (remoteAddress == null || remoteAddress.isDomainSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public ChannelHandler createHandler(VertxInternal vertx, ContextInternal ctx) {
    if (sslHelper.sni) {
      return createSniHandler(vertx, ctx);
    } else {
      return createSslHandler(vertx, null);
    }
  }

  public SniHandler createSniHandler(VertxInternal vertx, ContextInternal ctx) {
    Executor delegatedTaskExec = useWorkerPool ? vertx.getInternalWorkerPool().executor() : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(serverNameMapper(vertx, ctx), sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout), delegatedTaskExec);
  }
}
