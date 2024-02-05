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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.AsyncMapping;
import io.netty.util.concurrent.ImmediateExecutor;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SocketAddress;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLHelper {

  private static final EnumMap<ClientAuth, io.netty.handler.ssl.ClientAuth> CLIENT_AUTH_MAPPING = new EnumMap<>(ClientAuth.class);

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
  private final boolean sni;
  private final long sslHandshakeTimeout;
  private final TimeUnit sslHandshakeTimeoutUnit;
  private final boolean trustAll;
  private final ClientAuth clientAuth;
  private final boolean client;
  private final boolean useAlpn;
  private final Set<String> enabledProtocols;
  private final String endpointIdentificationAlgorithm;
  private final SSLEngineOptions sslEngineOptions;
  private final KeyCertOptions keyCertOptions;
  private final TrustOptions trustOptions;
  private final ArrayList<String> crlPaths;
  private final ArrayList<Buffer> crlValues;
  private final Set<String> enabledCipherSuites;
  private final List<String> applicationProtocols;
  private final boolean useWorkerPool;

  private Future<Supplier<SslContextFactory>> sslProvider;
  private SslContext[] sslContexts = new SslContext[2];
  private Map<String, SslContext>[] sslContextMaps = new Map[] {
    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
  };

  public SSLHelper(TCPSSLOptions options, List<String> applicationProtocols) {
    this.sslEngineOptions = options.getSslEngineOptions();
    this.crlPaths = new ArrayList<>(options.getCrlPaths());
    this.crlValues = new ArrayList<>(options.getCrlValues());
    this.enabledCipherSuites = new HashSet<>(options.getEnabledCipherSuites());
    this.ssl = options.isSsl();
    this.sslHandshakeTimeout = options.getSslHandshakeTimeout();
    this.sslHandshakeTimeoutUnit = options.getSslHandshakeTimeoutUnit();
    this.useAlpn = options.isUseAlpn();
    this.enabledProtocols = options.getEnabledSecureTransportProtocols();
    this.client = options instanceof ClientOptionsBase;
    this.trustAll = options instanceof ClientOptionsBase && ((ClientOptionsBase)options).isTrustAll();
    this.keyCertOptions = options.getKeyCertOptions() != null ? options.getKeyCertOptions().copy() : null;
    this.trustOptions = options.getTrustOptions() != null ? options.getTrustOptions().copy() : null;
    this.clientAuth = options instanceof NetServerOptions ? ((NetServerOptions)options).getClientAuth() : ClientAuth.NONE;
    this.endpointIdentificationAlgorithm = options instanceof NetClientOptions ? ((NetClientOptions)options).getHostnameVerificationAlgorithm() : "";
    this.sni = options instanceof NetServerOptions && ((NetServerOptions) options).isSni();
    this.applicationProtocols = applicationProtocols;
    this.useWorkerPool = sslEngineOptions == null ? SSLEngineOptions.DEFAULT_USE_WORKER_POOL : sslEngineOptions.getUseWorkerThread();
  }

  public synchronized int sniEntrySize() {
    return sslContextMaps[0].size() + sslContextMaps[1].size();
  }

  public boolean isSSL() {
    return ssl;
  }

  public boolean isSNI() {
    return sni;
  }

  private void configureEngine(SSLEngine engine, String serverName) {
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

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  public synchronized Future<Void> init(ContextInternal ctx) {
    Future<Supplier<SslContextFactory>> fut = sslProvider;
    if (fut == null) {
      if (keyCertOptions != null || trustOptions != null || trustAll || ssl) {
        Promise<Supplier<SslContextFactory>> promise = Promise.promise();
        fut = promise.future();
        ctx.<Void>executeBlockingInternal(p -> {
          KeyManagerFactory kmf;
          try {
            TrustManager[] mgrs = getTrustManagers(ctx.owner(), null);
            if (mgrs == null) {
              mgrs = getDefaultTrustManager(ctx.owner());
            }
            getTrustMgrFactory(ctx.owner(), mgrs);
            kmf = getDefaultKeyMgrFactory(ctx.owner());
          } catch (Exception e) {
            p.fail(e);
            return;
          }
          if (client || kmf != null) {
            p.complete();
          } else {
            p.fail("Key/certificate is mandatory for SSL");
          }
        }).compose(v2 -> ctx.<Supplier<SslContextFactory>>executeBlockingInternal(p -> {
          Supplier<SslContextFactory> sslProvider;
          try {
            SSLEngineOptions resolvedEngineOptions = resolveEngineOptions(sslEngineOptions, useAlpn);
            sslProvider = resolvedEngineOptions::sslContextFactory;
          } catch (Exception e) {
            p.fail(e);
            return;
          }
          p.complete(sslProvider);
        })).onComplete(promise);
      } else {
        fut = Future.succeededFuture(() -> new DefaultSslContextFactory(SslProvider.JDK, false));
      }
      sslProvider = fut;
    }
    PromiseInternal<Void> promise = ctx.promise();
    fut.<Void>mapEmpty().onComplete(promise);
    return promise.future();
  }

  public AsyncMapping<? super String, ? extends SslContext> serverNameMapper(ContextInternal ctx) {
    return (serverName, promise) -> {
      ctx.<SslContext>executeBlockingInternal(p -> {
        SslContext sslContext;
        try {
          sslContext = createContext(ctx.owner(), serverName, useAlpn, client, trustAll);
        } catch (Exception e) {
          p.fail(e);
          return;
        }
        if (sslContext != null) {
          sslContext = new DelegatingSslContext(sslContext) {
            @Override
            protected void initEngine(SSLEngine engine) {
              configureEngine(engine, serverName);
            }
          };
        }
        p.complete(sslContext);
      }, ar -> {
        if (ar.succeeded()) {
          promise.setSuccess(ar.result());
        } else {
          promise.setFailure(ar.cause());
        }
      });
      return promise;
    };
  }

  public SSLEngine createEngine(VertxInternal vertx) {
    SSLEngine engine = null;
    try {
      engine = createContext(vertx).newEngine(ByteBufAllocator.DEFAULT);
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw ((RuntimeException)e);
      } else {
        throw new VertxException(e);
      }
    }
    configureEngine(engine, null);
    return engine;
  }

  public SslContext createContext(VertxInternal vertx) {
    try {
      return createContext(vertx, null, useAlpn, client, trustAll);
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      } else {
        throw new VertxException(e);
      }
    }
  }

  private SslContext createContext(VertxInternal vertx, String serverName, boolean useAlpn, boolean client, boolean trustAll) throws Exception {
    TrustManager[] mgrs = getTrustManagers(vertx, serverName);
    KeyManagerFactory kmf = getKeyMgrFactory(vertx, serverName);
    int idx = useAlpn ? 0 : 1;
    if (serverName != null && (client || mgrs != null || kmf != null)) {
      if (mgrs == null) {
        if (trustAll) {
          mgrs = getTrustAllTrustManager();
        } else {
          mgrs = getDefaultTrustManager(vertx);
        }
      }
      KeyManagerFactory kmf2 = kmf == null ? getDefaultKeyMgrFactory(vertx) : kmf;
      TrustManagerFactory tmf = mgrs != null ? getTrustMgrFactory(vertx, mgrs) : null;
      return sslContextMaps[idx].computeIfAbsent(serverName, s -> createContext2(kmf2, tmf, serverName, useAlpn, client));
    }
    return createDefaultContext(vertx, trustAll);
  }

  private SslContext createDefaultContext(VertxInternal vertx, boolean trustAll) throws Exception {
    KeyManagerFactory kmf = getDefaultKeyMgrFactory(vertx);
    TrustManager[] mgrs = trustAll ? getTrustAllTrustManager() : getDefaultTrustManager(vertx);
    TrustManagerFactory tmf = mgrs != null ? getTrustMgrFactory(vertx, mgrs) : null;
    int idx = useAlpn ? 0 : 1;
    if (sslContexts[idx] == null) {
      sslContexts[idx] = createContext2(kmf, tmf, null, useAlpn, client);
    }
    return sslContexts[idx];
  }

  public SslContext sslContext(VertxInternal vertx, String serverName, boolean useAlpn) throws Exception {
    SslContext context = createContext(vertx, serverName, useAlpn, client, trustAll);
    return new DelegatingSslContext(context) {
      @Override
      protected void initEngine(SSLEngine engine) {
        configureEngine(engine, serverName);
      }
    };
  }

  private TrustManager[] getTrustManagers(VertxInternal vertx, String serverName) {
    try {
      TrustManager[] mgrs = null;
      if (trustOptions != null) {
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
        }
      }
      return mgrs;
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      } else {
        throw new VertxException(e);
      }
    }
  }

  private TrustManager[] getTrustAllTrustManager() {
    return new TrustManager[]{createTrustAllTrustManager()};
  }

  private TrustManager[] getDefaultTrustManager(VertxInternal vertx) {
    try {
      if (trustOptions != null) {
        TrustManagerFactory fact = trustOptions.getTrustManagerFactory(vertx);
        if (fact != null) {
          return fact.getTrustManagers();
        }
      }
      return null;
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      } else {
        throw new VertxException(e);
      }
    }
  }

  private SslContext createContext2(KeyManagerFactory kmf, TrustManagerFactory tmf, String serverName, boolean useAlpn, boolean client) {
    try {
      SslContextFactory factory = sslProvider.result().get()
        .useAlpn(useAlpn)
        .forClient(client)
        .enabledCipherSuites(enabledCipherSuites)
        .applicationProtocols(applicationProtocols);
      if (!client) {
        factory.clientAuth(CLIENT_AUTH_MAPPING.get(clientAuth));
      }
      if (kmf != null) {
        factory.keyMananagerFactory(kmf);
      }
      if (tmf != null) {
        factory.trustManagerFactory(tmf);
      }
      if (serverName != null) {
        factory.serverName(serverName);
      }
      return factory.create();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public SslHandler createSslHandler(VertxInternal vertx, String serverName) {
    return createSslHandler(vertx, null, serverName);
  }

  public SslHandler createSslHandler(VertxInternal vertx, SocketAddress remoteAddress, String serverName) {
    try {
      return createSslHandler(vertx, remoteAddress, serverName, useAlpn);
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      } else {
        throw new VertxException(e);
      }
    }
  }

  public SslHandler createSslHandler(VertxInternal vertx, SocketAddress remoteAddress, String serverName, boolean useAlpn) {
    SslContext sslContext = null;
    try {
      sslContext = sslContext(vertx, serverName, useAlpn);
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      } else {
        throw new VertxException(e);
      }
    }
    SslHandler sslHandler;
    Executor delegatedTaskExec = useWorkerPool ? vertx.getInternalWorkerPool().executor() : ImmediateExecutor.INSTANCE;
    if (remoteAddress == null || remoteAddress.isDomainSocket()) {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, delegatedTaskExec);
    } else {
      sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit);
    return sslHandler;
  }

  public SniHandler createSniHandler(ContextInternal ctx) {
    Executor delegatedTaskExec = useWorkerPool ? ctx.owner().getInternalWorkerPool().executor() : ImmediateExecutor.INSTANCE;
    return new VertxSniHandler(serverNameMapper(ctx), delegatedTaskExec, sslHandshakeTimeoutUnit.toMillis(sslHandshakeTimeout));
  }

  public ChannelHandler createHandler(ContextInternal ctx) {
    if (sni) {
      return createSniHandler(ctx);
    } else {
      return createSslHandler(ctx.owner(), null);
    }
  }

  private KeyManagerFactory getKeyMgrFactory(VertxInternal vertx, String serverName) throws Exception {
    KeyManagerFactory kmf = null;
    if (keyCertOptions != null) {
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
    }
    return kmf;
  }

  private KeyManagerFactory getDefaultKeyMgrFactory(VertxInternal vertx) throws Exception {
    return keyCertOptions == null ? null : keyCertOptions.getKeyManagerFactory(vertx);
  }

  private TrustManagerFactory getTrustMgrFactory(VertxInternal vertx, TrustManager[] mgrs) throws Exception {
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
      if (trustMgr instanceof  X509TrustManager) {
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
