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

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.*;
import io.netty.util.Mapping;
import io.vertx.core.VertxException;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLHelper {

  /**
   * Resolve the ssl engine options to use for properly running the configured options.
   */
  public static SSLEngineOptions resolveEngineOptions(TCPSSLOptions options) {
    SSLEngineOptions engineOptions = options.getSslEngineOptions();
    if (engineOptions == null) {
      if (options.isUseAlpn()) {
        if (JdkSSLEngineOptions.isAlpnAvailable()) {
          engineOptions = new JdkSSLEngineOptions();
        } else if (OpenSSLEngineOptions.isAlpnAvailable()) {
          engineOptions = new OpenSSLEngineOptions();
        }
      }
    }
    if (engineOptions == null) {
      engineOptions = new JdkSSLEngineOptions();
    } else if (engineOptions instanceof OpenSSLEngineOptions) {
      if (!OpenSsl.isAvailable()) {
        VertxException ex = new VertxException("OpenSSL is not available");
        Throwable cause = OpenSsl.unavailabilityCause();
        if (cause != null) {
          ex.initCause(cause);
        }
        throw ex;
      }
    }

    if (options.isUseAlpn()) {
      if (engineOptions instanceof JdkSSLEngineOptions) {
        if (!JdkSSLEngineOptions.isAlpnAvailable()) {
          throw new VertxException("ALPN not available for JDK SSL/TLS engine");
        }
      }
      if (engineOptions instanceof OpenSSLEngineOptions) {
        if (!OpenSSLEngineOptions.isAlpnAvailable()) {
          throw new VertxException("ALPN is not available for OpenSSL SSL/TLS engine");
        }
      }
    }
    return engineOptions;
  }

  private static final Logger log = LoggerFactory.getLogger(SSLHelper.class);

  private SslContextFactory sslContextFactory;
  private final boolean ssl;
  private boolean sni;
  private final long sslHandshakeTimeout;
  private final TimeUnit sslHandshakeTimeoutUnit;
  private boolean trustAll;
  private ClientAuth clientAuth = ClientAuth.NONE;
  private boolean client;
  private boolean useAlpn;
  private Set<String> enabledProtocols;
  private String endpointIdentificationAlgorithm = "";
  private SslContext[] sslContexts = new SslContext[2];
  private Map<String, SslContext>[] sslContextMaps = new Map[] {
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };

  private SSLHelper(TCPSSLOptions options) {
    this.ssl = options.isSsl();
    this.sslHandshakeTimeout = options.getSslHandshakeTimeout();
    this.sslHandshakeTimeoutUnit = options.getSslHandshakeTimeoutUnit();
    this.useAlpn = options.isUseAlpn();
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
  }

  private SSLHelper(ClientOptionsBase options) {
    this((TCPSSLOptions) options);
    this.client = true;
    this.trustAll = options.isTrustAll();
  }

  public SSLHelper(HttpClientOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions, List<String> applicationProtocols) {
    this(options);
    this.sslContextFactory = new SslContextFactoryImpl(options, keyCertOptions, trustOptions, applicationProtocols);
  }

  public SSLHelper(NetClientOptions options, SslContextFactory sslContextFactory) {
    this(options);
    this.endpointIdentificationAlgorithm = options.getHostnameVerificationAlgorithm();
    this.sslContextFactory = sslContextFactory;
  }

  public SSLHelper(NetServerOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions, List<String> applicationProtocols) {
    this(options);
    this.clientAuth = options.getClientAuth();
    this.client = false;
    this.sni = options.isSni();
    this.sslContextFactory = new SslContextFactoryImpl(options, keyCertOptions, trustOptions, applicationProtocols);
  }

  /**
   * Copy constructor, only configuration field are copied.
   */
  public SSLHelper(SSLHelper that) {
    this.ssl = that.ssl;
    this.sni = that.sni;
    this.sslHandshakeTimeout = that.sslHandshakeTimeout;
    this.sslHandshakeTimeoutUnit = that.sslHandshakeTimeoutUnit;
    this.trustAll = that.trustAll;
    this.clientAuth = that.clientAuth;
    this.client = that.client;
    this.useAlpn = that.useAlpn;
    this.enabledProtocols = that.enabledProtocols;
    this.endpointIdentificationAlgorithm = that.endpointIdentificationAlgorithm;
    this.sslContextFactory = new SslContextFactoryImpl((SslContextFactoryImpl) that.sslContextFactory);
  }

  public boolean isSSL() {
    return ssl;
  }

  public boolean isSNI() {
    return sni;
  }

  private void configureEngine(SSLEngine engine, String serverName) {
    engine.setUseClientMode(client);
    Set<String> protocols = new LinkedHashSet<>(enabledProtocols);
    protocols.retainAll(Arrays.asList(engine.getSupportedProtocols()));
    if (protocols.isEmpty()) {
      log.warn("no SSL/TLS protocols are enabled due to configuration restrictions");
    }
    engine.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
    if (!client) {
      switch (clientAuth) {
        case REQUEST: {
          engine.setWantClientAuth(true);
          break;
        }
        case REQUIRED: {
          engine.setNeedClientAuth(true);
          break;
        }
        case NONE: {
          engine.setNeedClientAuth(false);
          break;
        }
      }
    } else if (!endpointIdentificationAlgorithm.isEmpty()) {
      SSLParameters sslParameters = engine.getSSLParameters();
      sslParameters.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
      engine.setSSLParameters(sslParameters);
    }
    if (serverName != null) {
      SSLParameters sslParameters = engine.getSSLParameters();
      sslParameters.setServerNames(Collections.singletonList(new SNIHostName(serverName)));
      engine.setSSLParameters(sslParameters);
    }
  }

  // This is called to validate some of the SSL params as that only happens when the context is created
  public synchronized void validate(VertxInternal vertx) {
    if (ssl) {
      createContext(vertx, null, useAlpn, client, trustAll);
    }
  }

  public Mapping<? super String, ? extends SslContext> serverNameMapper(VertxInternal vertx) {
    return serverName -> {
      SslContext ctx = createContext(vertx, serverName, useAlpn, client, trustAll);
      if (ctx != null) {
        ctx = new DelegatingSslContext(ctx) {
          @Override
          protected void initEngine(SSLEngine engine) {
            configureEngine(engine, serverName);
          }
        };
      }
      return ctx;
    };
  }

  public SslContext createContext(VertxInternal vertx) {
    return createContext(vertx, null, useAlpn, client, trustAll);
  }

  public SslContext createContext(VertxInternal vertx, String serverName, boolean useAlpn, boolean client, boolean trustAll) {
    int idx = useAlpn ? 0 : 1;
    if (serverName == null) {
      if (sslContexts[idx] == null) {
        sslContexts[idx] = sslContextFactory.createContext(vertx, serverName, useAlpn, client, trustAll);
      }
      return sslContexts[idx];
    } else {
      return sslContextMaps[idx].computeIfAbsent(serverName, s -> sslContextFactory.createContext(vertx, serverName, useAlpn, client, trustAll));
    }
  }

  public SslHandler createSslHandler(VertxInternal vertx, String serverName) {
    return createSslHandler(vertx, null, serverName);
  }

  public SslHandler createSslHandler(VertxInternal vertx, SocketAddress remoteAddress, String serverName) {
    return createSslHandler(vertx, remoteAddress, serverName, useAlpn);
  }

  public SslHandler createSslHandler(VertxInternal vertx, SocketAddress remoteAddress, String serverName, boolean useAlpn) {
    SslContext sslContext = sslContext(vertx, serverName, useAlpn);
    SslHandler sslHandler;
    if (remoteAddress == null || remoteAddress.isDomainSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port());
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public SslContext sslContext(VertxInternal vertx, String serverName, boolean useAlpn) {
    SslContext context = createContext(vertx, null, useAlpn, client, trustAll);
    return new DelegatingSslContext(context) {
      @Override
      protected void initEngine(SSLEngine engine) {
        configureEngine(engine, serverName);
      }
    };
  }

  public SSLEngine createEngine(VertxInternal vertx, String host, int port) {
    SSLEngine engine = createContext(vertx).newEngine(ByteBufAllocator.DEFAULT, host, port);
    configureEngine(engine, null);
    return engine;
  }

  public SSLEngine createEngine(VertxInternal vertx) {
    SSLEngine engine = createContext(vertx).newEngine(ByteBufAllocator.DEFAULT);
    configureEngine(engine, null);
    return engine;
  }
}
