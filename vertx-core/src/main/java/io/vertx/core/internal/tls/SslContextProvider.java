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
import io.netty.util.Mapping;
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

  private final boolean useWorkerPool;
  private final Supplier<SslContextFactory> provider;
  private final Set<String> enabledProtocols;
  private final List<CRL> crls;
  private final ClientAuth clientAuth;
  private final Set<String> enabledCipherSuites;
  private final String endpointIdentificationAlgorithm;
  private final KeyManagerFactory keyManagerFactory;
  private final TrustManagerFactory trustManagerFactory;
  private final Function<String, KeyManagerFactory> keyManagerFactoryMapper;
  private final Function<String, TrustManager[]> trustManagerMapper;

  private Map<String, SslContext> sslContexts = new ConcurrentHashMap<>();
  private final Map<String, SslContext> sslContextMaps = new ConcurrentHashMap<>();

  public SslContextProvider(boolean useWorkerPool,
                            ClientAuth clientAuth,
                            String endpointIdentificationAlgorithm,
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
    return sslContextMaps.size();
  }

  public SslContext createContext(boolean server,
                                       KeyManagerFactory keyManagerFactory,
                                       TrustManager[] trustManagers,
                                       String serverName,
                                       List<String> applicationProtocols) {
    if (keyManagerFactory == null) {
      keyManagerFactory = defaultKeyManagerFactory();
    }
    if (trustManagers == null) {
      trustManagers = defaultTrustManagers();
    }
    if (server) {
      return createServerContext(keyManagerFactory, trustManagers, serverName, applicationProtocols);
    } else {
      return createClientContext(keyManagerFactory, trustManagers, serverName, applicationProtocols);
    }
  }

  public SslContext sslClientContext(String serverName, List<String> applicationProtocols) {
    try {
      return sslContext(serverName, applicationProtocols, false);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslContext sslContext(String serverName, List<String> applicationProtocols, boolean server) throws Exception {
    if (serverName != null) {
      KeyManagerFactory kmf = resolveKeyManagerFactory(serverName);
      TrustManager[] trustManagers = resolveTrustManagers(serverName);
      if (kmf != null || trustManagers != null || !server) {
        return sslContextMaps.computeIfAbsent(serverName, s -> createContext(server, kmf, trustManagers, s, applicationProtocols));
      }
    }
    String alpnKey;
    if (applicationProtocols == null) {
      alpnKey = "";
    } else {
      StringBuilder builder = new StringBuilder();
      builder.append('(');
      for (int i = 0; i < applicationProtocols.size();i++) {
        if (i > 0) {
          builder.append(',');
        }
        builder.append(applicationProtocols.get(i));
      }
      builder.append(')');
      alpnKey = builder.toString();
    }
    SslContext context = sslContexts.get(alpnKey);
    if (context == null) {
      context = createContext(server, null, null, serverName, applicationProtocols);
      sslContexts.putIfAbsent(alpnKey, context);
    }
    return context;
  }

  public SslContext sslServerContext(List<String> applicationProtocols) {
    try {
      return sslContext(null, applicationProtocols, true);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public Mapping<? super String, ? extends SslContext> serverNameMapping(List<String> applicationProtocols) {
    return (Mapping<String, SslContext>) serverName -> {
      try {
        return sslContext(serverName, applicationProtocols, true);
      } catch (Exception e) {
        // Log this
        return null;
      }
    };
  }

  /**
   * Server name {@link AsyncMapping} for {@link SniHandler}, mapping happens on a Vert.x worker thread.
   *
   * @return the {@link AsyncMapping}
   */
  public AsyncMapping<? super String, ? extends SslContext> serverNameAsyncMapping(Executor workerPool, List<String> applicationProtocols) {
    return (AsyncMapping<String, SslContext>) (serverName, promise) -> {
      workerPool.execute(() -> {
        SslContext sslContext;
        try {
          sslContext = sslContext(serverName, applicationProtocols, true);
        } catch (Exception e) {
          promise.setFailure(e);
          return;
        }
        promise.setSuccess(sslContext);
      });
      return promise;
    };
  }

  public SslContext createContext(boolean server, List<String> applicationProtocols) {
    return createContext(server, defaultKeyManagerFactory(), defaultTrustManagers(), null, applicationProtocols);
  }

  public SslContext createClientContext(
    KeyManagerFactory keyManagerFactory,
    TrustManager[] trustManagers,
    String serverName,
    List<String> applicationProtocols) {
    try {
      SslContextFactory factory = provider.get()
        .forClient(serverName, endpointIdentificationAlgorithm)
        .enabledProtocols(enabledProtocols)
        .enabledCipherSuites(enabledCipherSuites)
        .useAlpn(applicationProtocols != null)
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
                                        List<String> applicationProtocols) {
    try {
      SslContextFactory factory = provider.get()
        .forServer(SslContextManager.CLIENT_AUTH_MAPPING.get(clientAuth))
        .enabledProtocols(enabledProtocols)
        .enabledCipherSuites(enabledCipherSuites)
        .useAlpn(applicationProtocols != null)
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
