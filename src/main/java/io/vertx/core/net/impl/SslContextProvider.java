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

import io.netty.handler.ssl.SslContext;
import io.vertx.core.VertxException;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides Netty's {@link SslContext}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslContextProvider {

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

  public SslContextProvider(ClientAuth clientAuth,
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
    this.provider = provider;
    this.clientAuth = clientAuth;
    this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
    this.applicationProtocols = applicationProtocols;
    this.enabledCipherSuites = new HashSet<>(enabledCipherSuites);
    this.enabledProtocols = enabledProtocols;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
    this.keyManagerFactoryMapper = keyManagerFactoryMapper;
    this.trustManagerMapper = trustManagerMapper;
    this.crls = crls;
  }

  public VertxSslContext createClientContext(String serverName, boolean useAlpn, boolean trustAll) {
    try {
      SslContextFactory factory = provider.get()
        .useAlpn(useAlpn)
        .forClient(true)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(applicationProtocols);
      if (keyManagerFactory != null) {
        factory.keyMananagerFactory(keyManagerFactory);
      }
      TrustManager[] trustManagers = null;
      if (trustAll) {
        trustManagers = new TrustManager[] { createTrustAllTrustManager() };
      } else if (trustManagerFactory != null) {
        trustManagers = trustManagerFactory.getTrustManagers();
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
      };
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public VertxSslContext createServerContext(boolean useAlpn) {
    return createServerContext(keyManagerFactory, trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null, null, useAlpn);
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
      factory.clientAuth(SSLHelper.CLIENT_AUTH_MAPPING.get(clientAuth));
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
      };
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public KeyManagerFactory loadKeyManagerFactory(String serverName) throws Exception {
    if (keyManagerFactoryMapper != null) {
      return keyManagerFactoryMapper.apply(serverName);
    }
    return null;
  }

  /**
   * Resolve the {@link KeyManagerFactory} for the {@code serverName}, when a factory cannot be resolved, the default
   * factory is returned.
   * <br/>
   * This can block and should be executed on the appropriate thread.
   *
   * @param serverName the server name
   * @return the factory
   * @throws Exception anything that would prevent loading the factory
   */
  public KeyManagerFactory resolveKeyManagerFactory(String serverName) throws Exception {
    KeyManagerFactory kmf = loadKeyManagerFactory(serverName);
    if (kmf == null) {
      kmf = keyManagerFactory;
    }
    return kmf;
  }

  public TrustManager[] loadTrustManagers(String serverName) throws Exception {
    if (trustManagerMapper != null) {
      return trustManagerMapper.apply(serverName);
    }
    return null;
  }

  /**
   * Resolve the {@link TrustManager}[] for the {@code serverName}, when managers cannot be resolved, the default
   * managers are returned.
   * <br/>
   * This can block and should be executed on the appropriate thread.
   *
   * @param serverName the server name
   * @return the managers
   * @throws Exception anything that would prevent loading the managers
   */
  public TrustManager[] resolveTrustManagers(String serverName) throws Exception {
    TrustManager[] trustManagers = loadTrustManagers(serverName);
    if (trustManagers == null && trustManagerFactory != null) {
      trustManagers = trustManagerFactory.getTrustManagers();
    }
    return trustManagers;
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

  // Create a TrustManager which trusts everything
  private static TrustManager createTrustAllTrustManager() {
    return new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
  }

  public void configureEngine(SSLEngine engine, Set<String> enabledProtocols, String serverName, boolean client) {
    Set<String> protocols = new LinkedHashSet<>(enabledProtocols);
    protocols.retainAll(Arrays.asList(engine.getSupportedProtocols()));
    engine.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
    if (client && !endpointIdentificationAlgorithm.isEmpty()) {
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
}
