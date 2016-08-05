/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * This is a pretty sucky class - could do with a refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
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
      if (!OpenSSLEngineOptions.isAvailable()) {
        throw new VertxException("OpenSSL is not available");
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

  private static final Map<HttpVersion, String> PROTOCOL_NAME_MAPPING = new EnumMap<>(HttpVersion.class);
  private static final List<String> DEFAULT_JDK_CIPHER_SUITE;

  static {
    ArrayList<String> suite = new ArrayList<>();
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);
      SSLEngine engine = context.createSSLEngine();
      Collections.addAll(suite, engine.getEnabledCipherSuites());
    } catch (Throwable e) {
      suite = null;
    }
    DEFAULT_JDK_CIPHER_SUITE = suite != null ? Collections.unmodifiableList(suite) : null;
  }

  static {
    PROTOCOL_NAME_MAPPING.put(HttpVersion.HTTP_2, "h2");
    PROTOCOL_NAME_MAPPING.put(HttpVersion.HTTP_1_1, "http/1.1");
    PROTOCOL_NAME_MAPPING.put(HttpVersion.HTTP_1_0, "http/1.0");
  }

  private static final Logger log = LoggerFactory.getLogger(SSLHelper.class);

  // Make sure SSLv3 is NOT enabled due to POODLE vulnerability http://en.wikipedia.org/wiki/POODLE
  private static final String[] DEFAULT_ENABLED_PROTOCOLS = {"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"};

  private boolean ssl;
  private KeyCertOptions keyCertOptions;
  private TrustOptions trustOptions;
  private boolean trustAll;
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;
  private ClientAuth clientAuth = ClientAuth.NONE;
  private Set<String> enabledCipherSuites;
  private boolean openSsl;
  private boolean client;
  private boolean useAlpn;
  private List<HttpVersion> applicationProtocols;
  private Set<String> enabledProtocols;

  private String endpointIdentificationAlgorithm = "";

  private SslContext sslContext;
  private boolean openSslSessionCacheEnabled = true;

  public SSLHelper(HttpClientOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions) {
    SSLEngineOptions sslEngineOptions = resolveEngineOptions(options);
    this.ssl = options.isSsl();
    this.keyCertOptions = keyCertOptions;
    this.trustOptions = trustOptions;
    this.trustAll = options.isTrustAll();
    this.crlPaths = new ArrayList<>(options.getCrlPaths());
    this.crlValues = new ArrayList<>(options.getCrlValues());
    this.enabledCipherSuites = options.getEnabledCipherSuites();
    this.openSsl = sslEngineOptions instanceof OpenSSLEngineOptions;
    this.client = true;
    this.useAlpn = options.isUseAlpn();
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
    if (options.isVerifyHost()) {
      this.endpointIdentificationAlgorithm = "HTTPS";
    }
    this.openSslSessionCacheEnabled = (sslEngineOptions instanceof OpenSSLEngineOptions) && ((OpenSSLEngineOptions) sslEngineOptions).isSessionCacheEnabled();
  }

  public SSLHelper(HttpServerOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions) {
    SSLEngineOptions sslEngineOptions = resolveEngineOptions(options);
    this.ssl = options.isSsl();
    this.keyCertOptions = keyCertOptions;
    this.trustOptions = trustOptions;
    this.clientAuth = options.getClientAuth();
    this.crlPaths = options.getCrlPaths() != null ? new ArrayList<>(options.getCrlPaths()) : null;
    this.crlValues = options.getCrlValues() != null ? new ArrayList<>(options.getCrlValues()) : null;
    this.enabledCipherSuites = options.getEnabledCipherSuites();
    this.openSsl = sslEngineOptions instanceof OpenSSLEngineOptions;
    this.client = false;
    this.useAlpn = options.isUseAlpn();
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
    this.openSslSessionCacheEnabled = (sslEngineOptions instanceof OpenSSLEngineOptions) && ((OpenSSLEngineOptions) sslEngineOptions).isSessionCacheEnabled();
  }

  public SSLHelper(NetClientOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions) {
    SSLEngineOptions sslEngineOptions = resolveEngineOptions(options);
    this.ssl = options.isSsl();
    this.keyCertOptions = keyCertOptions;
    this.trustOptions = trustOptions;
    this.trustAll = options.isTrustAll();
    this.crlPaths = new ArrayList<>(options.getCrlPaths());
    this.crlValues = new ArrayList<>(options.getCrlValues());
    this.enabledCipherSuites = options.getEnabledCipherSuites();
    this.openSsl = sslEngineOptions instanceof OpenSSLEngineOptions;
    this.client = true;
    this.useAlpn = false;
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
    this.endpointIdentificationAlgorithm = options.getHostnameVerificationAlgorithm();
    this.openSslSessionCacheEnabled = (sslEngineOptions instanceof OpenSSLEngineOptions) && ((OpenSSLEngineOptions) sslEngineOptions).isSessionCacheEnabled();
  }

  public SSLHelper(NetServerOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions) {
    SSLEngineOptions sslEngineOptions = resolveEngineOptions(options);
    this.ssl = options.isSsl();
    this.keyCertOptions = keyCertOptions;
    this.trustOptions = trustOptions;
    this.clientAuth = options.getClientAuth();
    this.crlPaths = options.getCrlPaths() != null ? new ArrayList<>(options.getCrlPaths()) : null;
    this.crlValues = options.getCrlValues() != null ? new ArrayList<>(options.getCrlValues()) : null;
    this.enabledCipherSuites = options.getEnabledCipherSuites();
    this.openSsl = sslEngineOptions instanceof OpenSSLEngineOptions;
    this.client = false;
    this.useAlpn = false;
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
    this.openSslSessionCacheEnabled = (options.getSslEngineOptions() instanceof OpenSSLEngineOptions) && ((OpenSSLEngineOptions) options.getSslEngineOptions()).isSessionCacheEnabled();
  }

  public boolean isSSL() {
    return ssl;
  }

  public ClientAuth getClientAuth() {
    return clientAuth;
  }

  public List<HttpVersion> getApplicationProtocols() {
    return applicationProtocols;
  }

  public SSLHelper setApplicationProtocols(List<HttpVersion> applicationProtocols) {
    this.applicationProtocols = applicationProtocols;
    return this;
  }

  /*
    If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
    called jsssecacerts or cacerts in the JDK/JRE security directory.
    You can override this by specifying the javax.echo.ssl.trustStore system property

    If you don't specify a key store, and don't specify a system property no key store will be used
    You can override this by specifying the javax.echo.ssl.keyStore system property
     */
  private SslContext createContext(VertxInternal vertx) {
    try {
      KeyManagerFactory keyMgrFactory = getKeyMgrFactory(vertx);
      TrustManagerFactory trustMgrFactory = getTrustMgrFactory(vertx);
      SslContextBuilder builder;
      if (client) {
        builder = SslContextBuilder.forClient();
        if (keyMgrFactory != null) {
          builder.keyManager(keyMgrFactory);
        }
      } else {
        if (keyMgrFactory == null) {
          throw new VertxException("Key/certificate is mandatory for SSL");
        }
        builder = SslContextBuilder.forServer(keyMgrFactory);
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
          cipherSuites = DEFAULT_JDK_CIPHER_SUITE;
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
            applicationProtocols.stream().map(PROTOCOL_NAME_MAPPING::get).collect(Collectors.toList())
        ));
      }
      return builder.build();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  private KeyManagerFactory getKeyMgrFactory(VertxInternal vertx) throws Exception {
    return keyCertOptions == null ? null : keyCertOptions.getKeyManagerFactory(vertx);
  }

  private TrustManagerFactory getTrustMgrFactory(VertxInternal vertx) throws Exception {
    TrustManagerFactory fact;
    if (trustAll) {
      TrustManager[] mgrs = new TrustManager[]{createTrustAllTrustManager()};
      fact = new SimpleTrustManagerFactory() {
        @Override
        protected void engineInit(KeyStore keyStore) throws Exception {}

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {}

        @Override
        protected TrustManager[] engineGetTrustManagers() {
          return mgrs.clone();
        }
      };
    } else if (trustOptions != null) {
      fact = trustOptions.getTrustManagerFactory(vertx);
    } else {
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
      TrustManager[] mgrs = createUntrustRevokedCertTrustManager(fact.getTrustManagers(), crls);
      fact = new SimpleTrustManagerFactory() {
        @Override
        protected void engineInit(KeyStore keyStore) throws Exception {}

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {}

        @Override
        protected TrustManager[] engineGetTrustManagers() {
          return mgrs.clone();
        }
      };
    }
    return fact;
  }

  /*
  Proxy the specified trust managers with an implementation checking first the provided certificates
  against the the Certificate Revocation List (crl) before delegating to the original trust managers.
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

  private SslHandler createHandler(SSLEngine engine, boolean client) {
    if (enabledCipherSuites != null && !enabledCipherSuites.isEmpty()) {
      String[] toUse = enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]);
      engine.setEnabledCipherSuites(toUse);
    }
    engine.setUseClientMode(client);
    Set<String> protocols = new LinkedHashSet<>(Arrays.asList(DEFAULT_ENABLED_PROTOCOLS));
    protocols.retainAll(Arrays.asList(engine.getEnabledProtocols()));
    if (enabledProtocols != null && !enabledProtocols.isEmpty() && !protocols.isEmpty()) {
      protocols.retainAll(enabledProtocols);
      if (protocols.isEmpty()) {
        log.warn("no SSL/TLS protocols are enabled due to configuration restrictions");
      }
    }
    engine.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
    if (!client) {
      switch (getClientAuth()) {
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
    return new SslHandler(engine);
  }

  public SslContext getContext(VertxInternal vertx) {
    if (sslContext == null) {
      sslContext = createContext(vertx);
      if (sslContext instanceof OpenSslServerContext){
        SSLSessionContext sslSessionContext = sslContext.sessionContext();
        if (sslSessionContext instanceof OpenSslServerSessionContext){
          ((OpenSslServerSessionContext)sslSessionContext).setSessionCacheEnabled(openSslSessionCacheEnabled);
        }
      }
    }
    return sslContext;
  }

  // This is called to validate some of the SSL params as that only happens when the context is created
  public synchronized void validate(VertxInternal vertx) {
    if (ssl) {
      getContext(vertx);
    }
  }

  public SslHandler createSslHandler(VertxInternal vertx, String host, int port) {
    SSLEngine engine = getContext(vertx).newEngine(ByteBufAllocator.DEFAULT, host, port);
    return createHandler(engine, client);
  }

  public SslHandler createSslHandler(VertxInternal vertx) {
    SSLEngine engine = getContext(vertx).newEngine(ByteBufAllocator.DEFAULT);
    return createHandler(engine, client);
  }
}
