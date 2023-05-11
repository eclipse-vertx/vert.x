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

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.DefaultSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.security.cert.CRL;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLHelper {

  static final EnumMap<ClientAuth, io.netty.handler.ssl.ClientAuth> CLIENT_AUTH_MAPPING = new EnumMap<>(ClientAuth.class);

  static {
    CLIENT_AUTH_MAPPING.put(ClientAuth.REQUIRED, io.netty.handler.ssl.ClientAuth.REQUIRE);
    CLIENT_AUTH_MAPPING.put(ClientAuth.REQUEST, io.netty.handler.ssl.ClientAuth.OPTIONAL);
    CLIENT_AUTH_MAPPING.put(ClientAuth.NONE, io.netty.handler.ssl.ClientAuth.NONE);
  }

  /**
   * Resolve the ssl engine options to use for properly running the configured options.
   */
  public static SSLEngineOptions resolveEngineOptions(SSLEngineOptions engineOptions, boolean useAlpn) {
    if (engineOptions == null) {
      if (useAlpn) {
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

    if (useAlpn) {
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

  private final boolean ssl;
  private final boolean sni;
  private final boolean trustAll;
  private final ClientAuth clientAuth;
  private final boolean client;
  private final boolean useAlpn;
  private final String endpointIdentificationAlgorithm;
  private final SSLEngineOptions sslEngineOptions;
  private final List<String> applicationProtocols;
  private KeyManagerFactory keyManagerFactory;
  private TrustManagerFactory trustManagerFactory;
  private Function<String, KeyManagerFactory> keyManagerFactoryMapper;
  private Function<String, TrustManager[]> trustManagerMapper;
  private List<CRL> crls;

  public SSLHelper(TCPSSLOptions options, List<String> applicationProtocols) {
    this.sslEngineOptions = options.getSslEngineOptions();
    this.ssl = options.isSsl();
    this.useAlpn = options.isUseAlpn();
    this.client = options instanceof ClientOptionsBase;
    this.trustAll = options instanceof ClientOptionsBase && ((ClientOptionsBase)options).isTrustAll();
    this.clientAuth = options instanceof NetServerOptions ? ((NetServerOptions)options).getClientAuth() : ClientAuth.NONE;
    this.endpointIdentificationAlgorithm = options instanceof NetClientOptions ? ((NetClientOptions)options).getHostnameVerificationAlgorithm() : "";
    this.sni = options instanceof NetServerOptions && ((NetServerOptions) options).isSni();
    this.applicationProtocols = applicationProtocols;
  }

  private class EngineConfig {

    private final SSLOptions sslOptions;
    private final Supplier<SslContextFactory> supplier;
    private final boolean useWorkerPool;

    public EngineConfig(SSLOptions sslOptions, Supplier<SslContextFactory> supplier, boolean useWorkerPool) {
      this.sslOptions = sslOptions;
      this.supplier = supplier;
      this.useWorkerPool = useWorkerPool;
    }

    SslContextProvider sslContextProvider() {
      return new SslContextProvider(
        clientAuth,
        endpointIdentificationAlgorithm,
        applicationProtocols,
        sslOptions.getEnabledCipherSuites(),
        sslOptions.getEnabledSecureTransportProtocols(),
        keyManagerFactory,
        keyManagerFactoryMapper,
        trustManagerFactory,
        trustManagerMapper,
        crls,
        supplier);
    }
  }

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  public Future<SslContextProvider> buildContextProvider(SSLOptions sslOptions, ContextInternal ctx) {
    return build(new SSLOptions(sslOptions), ctx).map(EngineConfig::sslContextProvider);
  }

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  public Future<SslChannelProvider> buildChannelProvider(SSLOptions sslOptions, ContextInternal ctx) {
    return build(new SSLOptions(sslOptions), ctx).map(c -> new SslChannelProvider(
      c.sslContextProvider(), c.sslOptions.getSslHandshakeTimeout(), c.sslOptions.getSslHandshakeTimeoutUnit(), sni,
      trustAll,
      useAlpn,
      ctx.owner().getInternalWorkerPool().executor(),
      c.useWorkerPool
    ));
  }

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  private Future<EngineConfig> build(SSLOptions sslOptions, ContextInternal ctx) {
    Future<EngineConfig> sslContextFactorySupplier;
    KeyCertOptions keyCertOptions = sslOptions.getKeyCertOptions();
    TrustOptions trustOptions = sslOptions.getTrustOptions();
    if (keyCertOptions != null || trustOptions != null || trustAll || ssl) {
      Promise<EngineConfig> promise = Promise.promise();
      sslContextFactorySupplier = promise.future();
      ctx.<Void>executeBlockingInternal(p -> {
        try {
          if (sslOptions.getKeyCertOptions() != null) {
            keyManagerFactory = sslOptions.getKeyCertOptions().getKeyManagerFactory(ctx.owner());
            keyManagerFactoryMapper = sslOptions.getKeyCertOptions().keyManagerFactoryMapper(ctx.owner());
          }
          if (sslOptions.getTrustOptions() != null) {
            trustManagerFactory = sslOptions.getTrustOptions().getTrustManagerFactory(ctx.owner());
            trustManagerMapper = sslOptions.getTrustOptions().trustManagerMapper(ctx.owner());
          }
          crls = new ArrayList<>();
          List<Buffer> tmp = new ArrayList<>();
          if (sslOptions.getCrlPaths() != null) {
            tmp.addAll(sslOptions.getCrlPaths()
              .stream()
              .map(path -> ctx.owner().resolveFile(path).getAbsolutePath())
              .map(ctx.owner().fileSystem()::readFileBlocking)
              .collect(Collectors.toList()));
          }
          if (sslOptions.getCrlValues() != null) {
            tmp.addAll(sslOptions.getCrlValues());
          }
          CertificateFactory certificatefactory = CertificateFactory.getInstance("X.509");
          for (Buffer crlValue : tmp) {
            crls.addAll(certificatefactory.generateCRLs(new ByteArrayInputStream(crlValue.getBytes())));
          }
        } catch (Exception e) {
          p.fail(e);
          return;
        }
        if (client || sslOptions.getKeyCertOptions() != null) {
          p.complete();
        } else {
          p.fail("Key/certificate is mandatory for SSL");
        }
      }).compose(v2 -> ctx.<EngineConfig>executeBlockingInternal(p -> {
        Supplier<SslContextFactory> supplier;
        boolean useWorkerPool;
        try {
          SSLEngineOptions resolvedEngineOptions = resolveEngineOptions(sslEngineOptions, useAlpn);
          supplier = resolvedEngineOptions::sslContextFactory;
          useWorkerPool = resolvedEngineOptions.getUseWorkerThread();
        } catch (Exception e) {
          p.fail(e);
          return;
        }
        p.complete(new EngineConfig(sslOptions, supplier, useWorkerPool));
      })).onComplete(promise);
    } else {
      sslContextFactorySupplier = Future.succeededFuture(new EngineConfig(sslOptions, () -> new DefaultSslContextFactory(SslProvider.JDK, false), SSLEngineOptions.DEFAULT_USE_WORKER_POOL));
    }
    return sslContextFactorySupplier;
  }
}
