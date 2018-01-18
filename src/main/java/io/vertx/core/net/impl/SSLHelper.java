/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.security.cert.CRL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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

  private boolean ssl;
  private boolean sni;
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
  private Map<Certificate, SslContext> sslContextMap = new ConcurrentHashMap<>();
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
    this.sni = options.isSni();
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
    this.sni = options.isSni();
  }

  public boolean isSSL() {
    return ssl;
  }

  public boolean isSNI() {
    return sni;
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
  private SslContext createContext(VertxInternal vertx, X509KeyManager mgr, TrustManagerFactory trustMgrFactory) {
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

  private TrustManagerFactory getTrustMgrFactory(VertxInternal vertx, String serverName) throws Exception {
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

  public void configureEngine(SSLEngine engine, String serverName) {
    if (enabledCipherSuites != null && !enabledCipherSuites.isEmpty()) {
      String[] toUse = enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]);
      engine.setEnabledCipherSuites(toUse);
    }
    engine.setUseClientMode(client);
    Set<String> protocols = new LinkedHashSet<>(enabledProtocols);
    protocols.retainAll(Arrays.asList(engine.getSupportedProtocols()));
    if (protocols.isEmpty()) {
      log.warn("no SSL/TLS protocols are enabled due to configuration restrictions");
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
    if (serverName != null) {
      SSLParameters sslParameters = engine.getSSLParameters();
      sslParameters.setServerNames(Collections.singletonList(new SNIHostName(serverName)));
      engine.setSSLParameters(sslParameters);
    }
  }

  public SslContext getContext(VertxInternal vertx) {
    return getContext(vertx, null);
  }

  public SslContext getContext(VertxInternal vertx, String serverName) {
    if (serverName == null) {
      if (sslContext == null) {
        TrustManagerFactory trustMgrFactory = null;
        try {
          trustMgrFactory = getTrustMgrFactory(vertx, null);
        } catch (Exception e) {
          throw new VertxException(e);
        }
        sslContext = createContext(vertx, null, trustMgrFactory);
      }
      return sslContext;
    } else {
      X509KeyManager mgr;
      try {
        mgr = keyCertOptions.keyManagerMapper(vertx).apply(serverName);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (mgr == null) {
        return sslContext;
      }
      try {
        TrustManagerFactory trustMgrFactory = getTrustMgrFactory(vertx, serverName);
        return sslContextMap.computeIfAbsent(mgr.getCertificateChain(null)[0], s -> createContext(vertx, mgr, trustMgrFactory));
      } catch (Exception e) {
        throw new VertxException(e);
      }
    }
  }

  // This is called to validate some of the SSL params as that only happens when the context is created
  public synchronized void validate(VertxInternal vertx) {
    if (ssl) {
      getContext(vertx, null);
    }
  }

  public SSLEngine createEngine(SslContext sslContext) {
    SSLEngine engine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
    configureEngine(engine, null);
    return engine;
  }

  public SSLEngine createEngine(VertxInternal vertx, SocketAddress socketAddress, String serverName) {
    SslContext context = getContext(vertx, null);
    SSLEngine engine;
    if (socketAddress.path() != null) {
      engine = context.newEngine(ByteBufAllocator.DEFAULT);
    } else {
      engine = context.newEngine(ByteBufAllocator.DEFAULT, socketAddress.host(), socketAddress.port());
    }
    configureEngine(engine, serverName);
    return engine;
  }

  public SSLEngine createEngine(VertxInternal vertx, String host, int port, String serverName) {
    SSLEngine engine = getContext(vertx, null).newEngine(ByteBufAllocator.DEFAULT, host, port);
    configureEngine(engine, serverName);
    return engine;
  }

  public SSLEngine createEngine(VertxInternal vertx, String host, int port) {
    SSLEngine engine = getContext(vertx, null).newEngine(ByteBufAllocator.DEFAULT, host, port);
    configureEngine(engine, null);
    return engine;
  }

  public SSLEngine createEngine(VertxInternal vertx) {
    SSLEngine engine = getContext(vertx, null).newEngine(ByteBufAllocator.DEFAULT);
    configureEngine(engine, null);
    return engine;
  }
}
