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
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.DefaultSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  private static final Logger log = LoggerFactory.getLogger(SSLHelper.class);

  private final boolean ssl;
  final boolean sni;
  final boolean trustAll;
  final ClientAuth clientAuth;
  final boolean client;
  final boolean useAlpn;
  private final String endpointIdentificationAlgorithm;
  private final SSLEngineOptions sslEngineOptions;
  final List<String> applicationProtocols;

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

  public boolean isSSL() {
    return ssl;
  }

  public boolean isSNI() {
    return sni;
  }

  void configureEngine(SSLEngine engine, Set<String> enabledProtocols, String serverName) {
    Set<String> protocols = new LinkedHashSet<>(enabledProtocols);
    protocols.retainAll(Arrays.asList(engine.getSupportedProtocols()));
    if (protocols.isEmpty()) {
      log.warn("no SSL/TLS protocols are enabled due to configuration restrictions");
    }
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

  private static class EngineConfig {
    private final Supplier<SslContextFactory> supplier;
    private final boolean useWorkerPool;

    public EngineConfig(Supplier<SslContextFactory> supplier, boolean useWorkerPool) {
      this.supplier = supplier;
      this.useWorkerPool = useWorkerPool;
    }
  }

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  public Future<SslContextProvider> init(SSLOptions sslOptions, ContextInternal ctx) {
    Future<EngineConfig> sslContextFactorySupplier;
    KeyCertOptions keyCertOptions = sslOptions.getKeyCertOptions();
    TrustOptions trustOptions = sslOptions.getTrustOptions();
    if (keyCertOptions != null || trustOptions != null || trustAll || ssl) {
      Promise<EngineConfig> promise = Promise.promise();
      sslContextFactorySupplier = promise.future();
      ctx.<Void>executeBlockingInternal(p -> {
        KeyManagerFactory kmf;
        try {
          getTrustMgrFactory(ctx.owner(), sslOptions.getTrustOptions(), sslOptions.getCrlPaths(), sslOptions.getCrlValues(), null, false);
          kmf = getKeyMgrFactory(ctx.owner(), sslOptions.getKeyCertOptions());
        } catch (Exception e) {
          p.fail(e);
          return;
        }
        if (client || kmf != null) {
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
        p.complete(new EngineConfig(supplier, useWorkerPool));
      })).onComplete(promise);
    } else {
      sslContextFactorySupplier = Future.succeededFuture(new EngineConfig(() -> new DefaultSslContextFactory(SslProvider.JDK, false), SSLEngineOptions.DEFAULT_USE_WORKER_POOL));
    }
    return sslContextFactorySupplier.map(sslp -> new SslContextProvider(this, sslOptions, sslp.useWorkerPool, sslp.supplier));
  }

  static KeyManagerFactory getKeyMgrFactory(VertxInternal vertx, KeyCertOptions keyCertOptions, String serverName) throws Exception {
    KeyManagerFactory kmf = null;
    if (serverName != null) {
      X509KeyManager mgr = keyCertOptions.keyManagerMapper(vertx).apply(serverName);
      if (mgr != null) {
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore ks = KeyStore.getInstance(keyStoreType);
        ks.load(null, null);
        ks.setKeyEntry("key", mgr.getPrivateKey(null), new char[0], mgr.getCertificateChain(null));
        String keyAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        kmf = KeyManagerFactory.getInstance(keyAlgorithm);
        kmf.init(ks, new char[0]);
      }
    }
    if (kmf == null) {
      kmf = getKeyMgrFactory(vertx, keyCertOptions);
    }
    return kmf;
  }

  private static KeyManagerFactory getKeyMgrFactory(VertxInternal vertx, KeyCertOptions keyCertOptions) throws Exception {
    return keyCertOptions == null ? null : keyCertOptions.getKeyManagerFactory(vertx);
  }

  static TrustManagerFactory getTrustMgrFactory(VertxInternal vertx, TrustOptions trustOptions, List<String> crlPaths, List<Buffer> crlValues, String serverName, boolean trustAll) throws Exception {
    TrustManager[] mgrs = null;
    if (trustAll) {
      mgrs = new TrustManager[]{createTrustAllTrustManager()};
    } else if (trustOptions != null) {
      if (serverName != null) {
        Function<String, TrustManager[]> mapper = trustOptions.trustManagerMapper(vertx);
        if (mapper != null) {
          mgrs = mapper.apply(serverName);
        }
        if (mgrs == null) {
          TrustManagerFactory fact = trustOptions.getTrustManagerFactory(vertx);
          if (fact != null) {
            mgrs = fact.getTrustManagers();
          }
        }
      } else {
        TrustManagerFactory fact = trustOptions.getTrustManagerFactory(vertx);
        if (fact != null) {
          mgrs = fact.getTrustManagers();
        }
      }
    }
    if (mgrs == null) {
      return null;
    }
    if (crlPaths != null && crlValues != null && (crlPaths.size() > 0 || crlValues.size() > 0)) {
      Stream<Buffer> tmp = crlPaths.
        stream().
        map(path -> vertx.resolveFile(path).getAbsolutePath()).
        map(vertx.fileSystem()::readFileBlocking);
      tmp = Stream.concat(tmp, crlValues.stream());
      CertificateFactory certificatefactory = CertificateFactory.getInstance("X.509");
      ArrayList<CRL> crls = new ArrayList<>();
      for (Buffer crlValue : tmp.collect(Collectors.toList())) {
        crls.addAll(certificatefactory.generateCRLs(new ByteArrayInputStream(crlValue.getBytes())));
      }
      mgrs = createUntrustRevokedCertTrustManager(mgrs, crls);
    }
    return new VertxTrustManagerFactory(mgrs);
  }

  /*
  Proxy the specified trust managers with an implementation checking first the provided certificates
  against the Certificate Revocation List (crl) before delegating to the original trust managers.
   */
  static TrustManager[] createUntrustRevokedCertTrustManager(TrustManager[] trustMgrs, ArrayList<CRL> crls) {
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
}
