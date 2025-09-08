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

import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsyncMapping;
import io.vertx.core.VertxException;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
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

  private static final List<String> VALID_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3", "SSLv2Hello", "DTLSv1.2", "DTLSv1.0");

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

  private final SslContext[] sslContexts = new SslContext[2];
  private final Map<String, SslContext>[] sslContextMaps = new Map[]{
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };

  public SslContextProvider(boolean useWorkerPool,
                            ClientAuth clientAuth,
                            String endpointIdentificationAlgorithm,
                            List<String> applicationProtocols,
                            Set<String> enabledCipherSuites,
                            Set<String> enabledProtocols,
                            KeyManagerFactory keyManagerFactory,
                            Function<String, KeyManagerFactory> keyManagerFactoryMapper,
                            TrustManagerFactory trustManagerFactory,
                            Function<String, TrustManager[]> trustManagerMapper,
                            List<CRL> crls,
                            Supplier<SslContextFactory> provider) {

    // Filter the list of enabled protocols
    enabledProtocols = new HashSet<>(enabledProtocols);
    enabledProtocols.retainAll(VALID_PROTOCOLS);

    this.useWorkerPool = useWorkerPool;
    this.provider = provider;
    this.clientAuth = clientAuth;
    this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
    this.applicationProtocols = applicationProtocols;
    this.enabledCipherSuites = enabledCipherSuites;
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

  public SslContext createContext(boolean server,
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
        return sslContextMaps[idx].computeIfAbsent(serverName, s -> createContext(server, kmf, trustManagers, s, useAlpn));
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

  public SslContext createContext(boolean server, boolean useAlpn) {
    return createContext(server, defaultKeyManagerFactory(), defaultTrustManagers(), null, useAlpn);
  }

  public SslContext createClientContext(
    KeyManagerFactory keyManagerFactory,
    TrustManager[] trustManagers,
    String serverName,
    boolean useAlpn) {
    try {
      SslContextFactory factory = provider.get()
        .useAlpn(useAlpn)
        .forClient(serverName, endpointIdentificationAlgorithm)
        .enabledProtocols(enabledProtocols)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(applicationProtocols);
      if (keyManagerFactory != null) {
        factory.keyMananagerFactory(keyManagerFactory);
      }
      if (trustManagers != null) {
        TrustManagerFactory tmf = buildVertxTrustManagerFactory(trustManagers);
        factory.trustManagerFactory(tmf);
      }
      return factory.create();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslContext createServerContext(KeyManagerFactory keyManagerFactory,
                                        TrustManager[] trustManagers,
                                        String serverName,
                                        boolean useAlpn) {
    try {
      SslContextFactory factory = provider.get()
        .useAlpn(useAlpn)
        .forServer(SslContextManager.CLIENT_AUTH_MAPPING.get(clientAuth))
        .enabledProtocols(enabledProtocols)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(applicationProtocols);
      if (keyManagerFactory != null) {
        factory.keyMananagerFactory(keyManagerFactory);
      }
      if (trustManagers != null) {
        TrustManagerFactory tmf = buildVertxTrustManagerFactory(trustManagers);
        factory.trustManagerFactory(tmf);
      }
      return factory.create();
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
}
