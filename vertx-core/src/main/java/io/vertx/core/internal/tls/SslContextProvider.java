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

import io.netty.handler.ssl.SslContext;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides Netty's {@link SslContext}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class SslContextProvider {

  private static final List<String> VALID_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3", "SSLv2Hello", "DTLSv1.2", "DTLSv1.0");

  private final boolean useWorkerPool;
  private final List<CRL> crls;
  private final KeyManagerFactory keyManagerFactory;
  private final TrustManagerFactory trustManagerFactory;
  private final Function<String, KeyManagerFactory> keyManagerFactoryMapper;
  private final Function<String, TrustManager[]> trustManagerMapper;
  protected final Supplier<SslContextFactory> provider;
  protected final Set<String> enabledProtocols;
  protected final Set<String> enabledCipherSuites;

  private final Map<String, SslContext> sslContexts = new ConcurrentHashMap<>();
  private final Map<String, SslContext> sslContextMaps = new ConcurrentHashMap<>();

  public SslContextProvider(boolean useWorkerPool,
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

  protected abstract SslContext createContext(KeyManagerFactory keyManagerFactory,  TrustManager[] trustManagers, String serverName, List<String> applicationProtocols);

  protected SslContext sslContext(String serverName, List<String> applicationProtocols, boolean server) throws Exception {
    if (serverName != null) {
      KeyManagerFactory kmf = resolveKeyManagerFactory(serverName);
      TrustManager[] trustManagers = resolveTrustManagers(serverName);
      if (kmf != null || trustManagers != null || !server) {
        return sslContextMaps.computeIfAbsent(serverName, s -> {
          return createContext(kmf, trustManagers, s, applicationProtocols);
        });
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
      context = createContext(null, null, null, applicationProtocols);
      sslContexts.putIfAbsent(alpnKey, context);
    }
    return context;
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

  protected final VertxTrustManagerFactory buildVertxTrustManagerFactory(TrustManager[] mgrs) {
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
