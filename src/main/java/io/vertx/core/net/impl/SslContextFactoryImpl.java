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

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.security.cert.CRL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default implementation of {@link SslContextFactory} that creates and configures a Netty {@link SslContext} with
 * the provided options.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SslContextFactoryImpl implements SslContextFactory {

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

  private final KeyCertOptions keyCertOptions;
  private final TrustOptions trustOptions;
  private final ArrayList<String> crlPaths;
  private final ArrayList<Buffer> crlValues;
  private final Set<String> enabledCipherSuites;
  private final boolean openSsl;
  private final List<String> applicationProtocols;
  private final boolean openSslSessionCacheEnabled;

  public SslContextFactoryImpl(TCPSSLOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions, List<String> applicationProtocols) {
    SSLEngineOptions sslEngineOptions = resolveEngineOptions(options);
    this.keyCertOptions = keyCertOptions;
    this.trustOptions = trustOptions;
    this.crlPaths = new ArrayList<>(options.getCrlPaths());
    this.crlValues = new ArrayList<>(options.getCrlValues());
    this.enabledCipherSuites = options.getEnabledCipherSuites();
    this.openSsl = sslEngineOptions instanceof OpenSSLEngineOptions;
    this.openSslSessionCacheEnabled = (sslEngineOptions instanceof OpenSSLEngineOptions) && ((OpenSSLEngineOptions) sslEngineOptions).isSessionCacheEnabled();
    this.applicationProtocols = applicationProtocols;
  }

  /**
   * Copy constructor, only configuration field are copied.
   */
  public SslContextFactoryImpl(SslContextFactoryImpl that) {
    this.keyCertOptions = that.keyCertOptions;
    this.trustOptions = that.trustOptions;
    this.crlPaths = that.crlPaths;
    this.crlValues = that.crlValues;
    this.enabledCipherSuites = that.enabledCipherSuites;
    this.openSsl = that.openSsl;
    this.applicationProtocols = that.applicationProtocols;
    this.openSslSessionCacheEnabled = that.openSslSessionCacheEnabled;
  }

  /*
    If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
    called jsssecacerts or cacerts in the JDK/JRE security directory.
    You can override this by specifying the javax.echo.ssl.trustStore system property

    If you don't specify a key store, and don't specify a system property no key store will be used
    You can override this by specifying the javax.echo.ssl.keyStore system property
     */
  private SslContext createContext(VertxInternal vertx, boolean useAlpn, boolean client, X509KeyManager mgr, TrustManagerFactory trustMgrFactory) {
    try {
      SslContextBuilder builder;
      if (client) {
        builder = SslContextBuilder.forClient();
        KeyManagerFactory keyMgrFactory = getKeyMgrFactory(vertx);
        if (keyMgrFactory != null) {
          builder.keyManager(keyMgrFactory);
        }
      } else {
        if (mgr != null) {
          builder = SslContextBuilder.forServer(mgr.getPrivateKey(null), null, mgr.getCertificateChain(null));
        } else {
          KeyManagerFactory keyMgrFactory = getKeyMgrFactory(vertx);
          if (keyMgrFactory == null) {
            throw new VertxException("Key/certificate is mandatory for SSL");
          }
          builder = SslContextBuilder.forServer(keyMgrFactory);
        }
      }
      Collection<String> cipherSuites = enabledCipherSuites;
      if (openSsl) {
        builder.sslProvider(SslProvider.OPENSSL);
        if (cipherSuites == null || cipherSuites.isEmpty()) {
          cipherSuites = OpenSsl.availableOpenSslCipherSuites();
        }
      } else {
        builder.sslProvider(SslProvider.JDK);
        if (cipherSuites == null || cipherSuites.isEmpty()) {
          cipherSuites = DefaultJDKCipherSuite.get();
        }
      }
      if (trustMgrFactory != null) {
        builder.trustManager(trustMgrFactory);
      }
      if (cipherSuites != null && cipherSuites.size() > 0) {
        builder.ciphers(cipherSuites);
      }
      if (useAlpn && applicationProtocols != null && applicationProtocols.size() > 0) {
        builder.applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            applicationProtocols
        ));
      }
      SslContext ctx = builder.build();
      if (ctx instanceof OpenSslServerContext){
        SSLSessionContext sslSessionContext = ctx.sessionContext();
        if (sslSessionContext instanceof OpenSslServerSessionContext){
          ((OpenSslServerSessionContext)sslSessionContext).setSessionCacheEnabled(openSslSessionCacheEnabled);
        }
      }
      return ctx;
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  private KeyManagerFactory getKeyMgrFactory(VertxInternal vertx) throws Exception {
    return keyCertOptions == null ? null : keyCertOptions.getKeyManagerFactory(vertx);
  }

  private TrustManagerFactory getTrustMgrFactory(VertxInternal vertx, String serverName, boolean trustAll) throws Exception {
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
  private static TrustManager[] createUntrustRevokedCertTrustManager(TrustManager[] trustMgrs, ArrayList<CRL> crls) {
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

  public SslContext createContext(VertxInternal vertx, String serverName, boolean useAlpn, boolean client, boolean trustAll) {
    if (serverName == null) {
      TrustManagerFactory trustMgrFactory;
      try {
        trustMgrFactory = getTrustMgrFactory(vertx, null, trustAll);
      } catch (Exception e) {
        throw new VertxException(e);
      }
      return createContext(vertx, useAlpn, client, null, trustMgrFactory);
    } else {
      X509KeyManager mgr;
      try {
        mgr = keyCertOptions.keyManagerMapper(vertx).apply(serverName);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (mgr == null) {
        // Could it return null ???
        return createContext(vertx, null, useAlpn, client, trustAll);
      }
      try {
        TrustManagerFactory trustMgrFactory = getTrustMgrFactory(vertx, serverName, trustAll);
        return createContext(vertx, useAlpn, client, mgr, trustMgrFactory);
      } catch (Exception e) {
        throw new VertxException(e);
      }
    }
  }
}
