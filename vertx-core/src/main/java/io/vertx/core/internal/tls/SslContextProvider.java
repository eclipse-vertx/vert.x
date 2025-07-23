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
package io.vertx.core.internal.tls;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicSslEngine;
import io.netty.util.AsyncMapping;
import io.netty.util.Mapping;
import io.vertx.core.VertxException;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.net.VertxSslContext;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.Http3Utils;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides Netty's {@link SslContext}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslContextProvider {

  private static int idx(boolean useAlpn) {
    return useAlpn ? 0 : 1;
  }

  private final boolean useWorkerPool;
  private final Supplier<SslContextFactory> provider;
  private final Set<String> enabledProtocols;
  private final List<CRL> crls;
  private final ClientAuth clientAuth;
  private final Set<String> enabledCipherSuites;
  private final List<String> applicationProtocols;
  private final String endpointIdentificationAlgorithm;
  private final KeyManagerFactory keyManagerFactory;
  private final TrustManagerFactory trustManagerFactory;
  private final Function<String, KeyManagerFactory> keyManagerFactoryMapper;
  private final Function<String, TrustManager[]> trustManagerMapper;
  private final Http3Utils.QuicCodecBuilderInitializer quicCodecBuilderInitializer;

  private final SslContext[] sslContexts = new SslContext[2];
  private final Map<String, SslContext>[] sslContextMaps = new Map[]{
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };

  public SslContextProvider(boolean useWorkerPool,
                            ClientAuth clientAuth,
                            String endpointIdentificationAlgorithm,
                            List<String> applicationProtocols,
                            Http3Utils.QuicCodecBuilderInitializer quicCodecBuilderInitializer,
                            Set<String> enabledCipherSuites,
                            Set<String> enabledProtocols,
                            KeyManagerFactory keyManagerFactory,
                            Function<String, KeyManagerFactory> keyManagerFactoryMapper,
                            TrustManagerFactory trustManagerFactory,
                            Function<String, TrustManager[]> trustManagerMapper,
                            List<CRL> crls,
                            Supplier<SslContextFactory> provider) {
    this.useWorkerPool = useWorkerPool;
    this.provider = provider;
    this.clientAuth = clientAuth;
    this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
    this.applicationProtocols = applicationProtocols;
    this.quicCodecBuilderInitializer = quicCodecBuilderInitializer;
    this.enabledCipherSuites = new HashSet<>(enabledCipherSuites);
    this.enabledProtocols = enabledProtocols;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
    this.keyManagerFactoryMapper = keyManagerFactoryMapper;
    this.trustManagerMapper = trustManagerMapper;
    this.crls = crls;
  }

  public boolean useWorkerPool() {
    return useWorkerPool;
  }

  public int sniEntrySize() {
    return sslContextMaps[0].size() + sslContextMaps[1].size();
  }

  public VertxSslContext createContext(boolean server,
                                       KeyManagerFactory keyManagerFactory,
                                       TrustManager[] trustManagers,
                                       String serverName,
                                       boolean useAlpn) {
    if (keyManagerFactory == null) {
      keyManagerFactory = defaultKeyManagerFactory();
    }
    if (trustManagers == null) {
      trustManagers = defaultTrustManagers();
    }
    if (server) {
      return createServerContext(keyManagerFactory, trustManagers, serverName, useAlpn);
    } else {
      return createClientContext(keyManagerFactory, trustManagers, serverName, useAlpn);
    }
  }

  public SslContext sslClientContext(String serverName, boolean useAlpn) {
    try {
      return sslContext(serverName, useAlpn, false);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslContext sslContext(String serverName, boolean useAlpn, boolean server) throws Exception {
    int idx = idx(useAlpn);
    if (serverName != null) {
      KeyManagerFactory kmf = resolveKeyManagerFactory(serverName);
      TrustManager[] trustManagers = resolveTrustManagers(serverName);
      if (kmf != null || trustManagers != null || !server) {
        return sslContextMaps[idx].computeIfAbsent(serverName, s -> createContext(server, kmf, trustManagers, s,
          useAlpn));
      }
    }
    if (sslContexts[idx] == null) {
      SslContext context = createContext(server, null, null, serverName, useAlpn);
      sslContexts[idx] = context;
    }
    return sslContexts[idx];
  }

  public SslContext sslServerContext(boolean useAlpn) {
    try {
      return sslContext(null, useAlpn, true);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslContext quicSniSslServerContext(boolean useAlpn) {
    try {
      SslContext sniSslContext = QuicSslContextBuilder.buildForServerWithSni(serverNameMapping(useAlpn));

      return new VertxSslContext(sniSslContext) {
        @Override
        protected void initEngine(SSLEngine engine) {
          configureEngine(engine, enabledProtocols, null, false);
        }

        @Override
        public SslHandler newHandler(ByteBufAllocator alloc, Executor executor) {
          QuicSslEngine sslEngine = (QuicSslEngine) newEngine(alloc);
          return Http3Utils.newQuicServerSslHandler(sslEngine, executor, sniSslContext, quicCodecBuilderInitializer);
        }

        @Override
        public SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, Executor executor) {
          QuicSslEngine sslEngine = (QuicSslEngine) newEngine(alloc, peerHost, peerPort);
          return Http3Utils.newQuicServerSslHandler(sslEngine, executor, sniSslContext, quicCodecBuilderInitializer);
        }
      };
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  /**
   * Server name {@link AsyncMapping} for {@link SniHandler}, mapping happens on a Vert.x worker thread.
   *
   * @return the {@link AsyncMapping}
   */
  public AsyncMapping<? super String, ? extends SslContext> serverNameMapping(Executor workerPool, boolean useAlpn) {
    return (AsyncMapping<String, SslContext>) (serverName, promise) -> {
      workerPool.execute(() -> {
        SslContext sslContext;
        try {
          sslContext = sslContext(serverName, useAlpn, true);
        } catch (Exception e) {
          promise.setFailure(e);
          return;
        }
        promise.setSuccess(sslContext);
      });
      return promise;
    };
  }

  /**
   * Server name for {@link SniHandler}
   *
   * @return the {@link Mapping}
   */
  public Mapping<? super String, ? extends QuicSslContext> serverNameMapping(boolean useAlpn) {
    return (Mapping<String, QuicSslContext>) serverName -> {
      try {
        VertxSslContext sslContext = (VertxSslContext) sslContext(serverName, useAlpn, true);
        return (QuicSslContext) sslContext.unwrap();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  public VertxSslContext createContext(boolean server, boolean useAlpn) {
    return createContext(server, defaultKeyManagerFactory(), defaultTrustManagers(), null, useAlpn);
  }

  public VertxSslContext createClientContext(
    KeyManagerFactory keyManagerFactory,
    TrustManager[] trustManagers,
    String serverName,
    boolean useAlpn) {
    try {
      SslContextFactory factory = provider.get()
        .useAlpn(useAlpn)
        .forClient(true)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(applicationProtocols);
      if (keyManagerFactory != null) {
        factory.keyMananagerFactory(keyManagerFactory);
      }
      if (trustManagers != null) {
        TrustManagerFactory tmf = buildVertxTrustManagerFactory(trustManagers);
        factory.trustManagerFactory(tmf);
      }
      SslContext context = factory.create();
      return new VertxSslContext(context) {
        @Override
        protected void initEngine(SSLEngine engine) {
          configureEngine(engine, enabledProtocols, serverName, true);
        }

        @Override
        public SslHandler newHandler(ByteBufAllocator alloc, Executor executor) {
          if (HttpUtils.supportsQuic(applicationProtocols)) {
            QuicSslEngine sslEngine = (QuicSslEngine) context.newEngine(alloc);
            return Http3Utils.newQuicClientSslHandler(sslEngine, executor, context, quicCodecBuilderInitializer);
          }
          return super.newHandler(alloc, executor);
        }

        @Override
        protected SslHandler newHandler(ByteBufAllocator alloc, boolean startTls, Executor executor) {
          if (HttpUtils.supportsQuic(applicationProtocols)) {
            QuicSslEngine sslEngine = (QuicSslEngine) context.newEngine(alloc);
            return Http3Utils.newQuicClientSslHandler(sslEngine, executor, context, quicCodecBuilderInitializer);
          }
          return super.newHandler(alloc, startTls, executor);
        }

        @Override
        protected SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, boolean startTls, Executor executor) {
          if (HttpUtils.supportsQuic(applicationProtocols)) {
            QuicSslEngine sslEngine = (QuicSslEngine) context.newEngine(alloc, peerHost, peerPort);
            return Http3Utils.newQuicClientSslHandler(sslEngine, executor, context, quicCodecBuilderInitializer);
          }
          return super.newHandler(alloc, peerHost, peerPort, startTls, executor);
        }
      };
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public VertxSslContext createServerContext(KeyManagerFactory keyManagerFactory,
                                        TrustManager[] trustManagers,
                                        String serverName,
                                        boolean useAlpn) {
    try {
      SslContextFactory factory = provider.get()
        .useAlpn(useAlpn)
        .forClient(false)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(applicationProtocols);
      factory.clientAuth(SslContextManager.CLIENT_AUTH_MAPPING.get(clientAuth));
      if (serverName != null) {
        factory.serverName(serverName);
      }
      if (keyManagerFactory != null) {
        factory.keyMananagerFactory(keyManagerFactory);
      }
      if (trustManagers != null) {
        TrustManagerFactory tmf = buildVertxTrustManagerFactory(trustManagers);
        factory.trustManagerFactory(tmf);
      }
      SslContext context = factory.create();
      return new VertxSslContext(context) {
        @Override
        protected void initEngine(SSLEngine engine) {
          configureEngine(engine, enabledProtocols, serverName, false);
        }

        @Override
        public SslHandler newHandler(ByteBufAllocator alloc, Executor executor) {
          if (HttpUtils.supportsQuic(applicationProtocols)) {
            QuicSslEngine sslEngine = (QuicSslEngine) newEngine(alloc);
            return Http3Utils.newQuicServerSslHandler(sslEngine, executor, context, quicCodecBuilderInitializer);
          }
          return super.newHandler(alloc, executor);
        }

        @Override
        public SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, Executor executor) {
          if (HttpUtils.supportsQuic(applicationProtocols)) {
            QuicSslEngine sslEngine = (QuicSslEngine) newEngine(alloc, peerHost, peerPort);
            return Http3Utils.newQuicServerSslHandler(sslEngine, executor, context, quicCodecBuilderInitializer);
          }
          return super.newHandler(alloc, peerHost, peerPort, executor);
        }
      };
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public TrustManager[] defaultTrustManagers() {
    return trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null;
  }

  public TrustManagerFactory defaultTrustManagerFactory() {
    return trustManagerFactory;
  }

  public KeyManagerFactory defaultKeyManagerFactory() {
    return keyManagerFactory;
  }

  /**
   * Resolve the {@link KeyManagerFactory} for the {@code serverName}, when a factory cannot be resolved, {@code null} is returned.
   * <br/>
   * This can block and should be executed on the appropriate thread.
   *
   * @param serverName the server name
   * @return the factory
   * @throws Exception anything that would prevent loading the factory
   */
  public KeyManagerFactory resolveKeyManagerFactory(String serverName) throws Exception {
    if (keyManagerFactoryMapper != null) {
      return keyManagerFactoryMapper.apply(serverName);
    }
    return null;
  }

  /**
   * Resolve the {@link TrustManager}[] for the {@code serverName}, when managers cannot be resolved, {@code null} is returned.
   * <br/>
   * This can block and should be executed on the appropriate thread.
   *
   * @param serverName the server name
   * @return the managers
   * @throws Exception anything that would prevent loading the managers
   */
  public TrustManager[] resolveTrustManagers(String serverName) throws Exception {
    if (trustManagerMapper != null) {
      return trustManagerMapper.apply(serverName);
    }
    return null;
  }

  private VertxTrustManagerFactory buildVertxTrustManagerFactory(TrustManager[] mgrs) {
    if (crls != null && crls.size() > 0) {
      mgrs = createUntrustRevokedCertTrustManager(mgrs, crls);
    }
    return new VertxTrustManagerFactory(mgrs);
  }

  /*
  Proxy the specified trust managers with an implementation checking first the provided certificates
  against the Certificate Revocation List (crl) before delegating to the original trust managers.
   */
  private static TrustManager[] createUntrustRevokedCertTrustManager(TrustManager[] trustMgrs, List<CRL> crls) {
    trustMgrs = trustMgrs.clone();
    for (int i = 0;i < trustMgrs.length;i++) {
      TrustManager trustMgr = trustMgrs[i];
      if (trustMgr instanceof X509TrustManager) {
        X509TrustManager x509TrustManager = (X509TrustManager) trustMgr;
        trustMgrs[i] = new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            checkRevoked(x509Certificates);
            x509TrustManager.checkClientTrusted(x509Certificates, s);
          }
          @Override
          public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            checkRevoked(x509Certificates);
            x509TrustManager.checkServerTrusted(x509Certificates, s);
          }
          private void checkRevoked(X509Certificate[] x509Certificates) throws CertificateException {
            for (X509Certificate cert : x509Certificates) {
              for (CRL crl : crls) {
                if (crl.isRevoked(cert)) {
                  throw new CertificateException("Certificate revoked");
                }
              }
            }
          }
          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return x509TrustManager.getAcceptedIssuers();
          }
        };
      }
    }
    return trustMgrs;
  }

  public void configureEngine(SSLEngine engine, Set<String> enabledProtocols, String serverName, boolean client) {
    Set<String> protocols = new LinkedHashSet<>(enabledProtocols);
    protocols.retainAll(Arrays.asList(engine.getSupportedProtocols()));
    if (HttpUtils.supportsQuic(applicationProtocols)) {
      return;
    }
    engine.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
    if (client) {
      SSLParameters sslParameters = engine.getSSLParameters();
      sslParameters.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm != null ? endpointIdentificationAlgorithm : "");
      engine.setSSLParameters(sslParameters);
    }
    if (serverName != null) {
      SSLParameters sslParameters = engine.getSSLParameters();
      sslParameters.setServerNames(Collections.singletonList(new SNIHostName(serverName)));
      engine.setSSLParameters(sslParameters);
    }
  }
}
