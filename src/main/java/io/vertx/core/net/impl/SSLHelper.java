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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.security.cert.CRL;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLHelper {

  private static final Config NULL_CONFIG = new Config(null, null, null, null, null);
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

  private final Supplier<SslContextFactory> supplier;
  private final boolean useWorkerPool;
  private final Map<ConfigKey, Future<Config>> configMap;
  private final Map<ConfigKey, Future<SslChannelProvider>> sslChannelProviderMap;

  public SSLHelper(SSLEngineOptions sslEngineOptions, int cacheMaxSize) {
    this.configMap = new LruCache<>(cacheMaxSize);
    this.sslChannelProviderMap = new LruCache<>(cacheMaxSize);
    this.supplier = sslEngineOptions::sslContextFactory;
    this.useWorkerPool = sslEngineOptions.getUseWorkerThread();
  }

  public synchronized int sniEntrySize() {
    int size = 0;
    for (Future<SslChannelProvider> fut : sslChannelProviderMap.values()) {
      SslChannelProvider result = fut.result();
      if (result != null) {
        size += result.sniEntrySize();
      }
    }
    return size;
  }

  public SSLHelper(SSLEngineOptions sslEngineOptions) {
    this(sslEngineOptions, 256);
  }

  public Future<SslChannelProvider> resolveSslChannelProvider(SSLOptions options, String endpointIdentificationAlgorithm, boolean useSNI, ClientAuth clientAuth, List<String> applicationProtocols, ContextInternal ctx) {
    return resolveSslChannelProvider(options, endpointIdentificationAlgorithm, useSNI, clientAuth, applicationProtocols, false, ctx);
  }

  public Future<SslChannelProvider> resolveSslChannelProvider(SSLOptions options, String hostnameVerificationAlgorithm, boolean useSNI, ClientAuth clientAuth, List<String> applicationProtocols, boolean force, ContextInternal ctx) {
    Promise<SslChannelProvider> promise;
    ConfigKey k = new ConfigKey(options);
    synchronized (this) {
      if (force) {
        sslChannelProviderMap.remove(k);
      } else {
        Future<SslChannelProvider> v = sslChannelProviderMap.get(k);
        if (v != null) {
          return v;
        }
      }
      promise = Promise.promise();
      sslChannelProviderMap.put(k, promise.future());
    }
    buildChannelProvider(options, hostnameVerificationAlgorithm, useSNI, clientAuth, applicationProtocols, force, ctx)
      .onComplete(promise);
    return promise.future();
  }

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  Future<SslContextProvider> buildContextProvider(SSLOptions sslOptions,
                                                  String hostnameVerificationAlgorithm,
                                                  ClientAuth clientAuth,
                                                  List<String> applicationProtocols,
                                                  boolean force,
                                                  ContextInternal ctx) {
    return buildConfig(sslOptions, force, ctx).map(config -> buildSslContextProvider(sslOptions, hostnameVerificationAlgorithm, supplier, clientAuth, applicationProtocols, config));
  }

  private SslContextProvider buildSslContextProvider(SSLOptions sslOptions, String hostnameVerificationAlgorithm, Supplier<SslContextFactory> supplier, ClientAuth clientAuth, List<String> applicationProtocols, Config config) {
    if (clientAuth == null && hostnameVerificationAlgorithm == null) {
      throw new VertxException("Missing hostname verification algorithm: you must set TCP client options host name" +
        " verification algorithm");
    }
    return new SslContextProvider(
      clientAuth,
      hostnameVerificationAlgorithm,
      applicationProtocols,
      sslOptions.getEnabledCipherSuites(),
      sslOptions.getEnabledSecureTransportProtocols(),
      config.keyManagerFactory,
      config.keyManagerFactoryMapper,
      config.trustManagerFactory,
      config.trustManagerMapper,
      config.crls,
      supplier);
  }

  /**
   * Initialize the helper, this loads and validates the configuration.
   *
   * @param ctx the context
   * @return a future resolved when the helper is initialized
   */
  protected Future<SslChannelProvider> buildChannelProvider(SSLOptions sslOptions,
                                                            String endpointIdentificationAlgorithm,
                                                            boolean useSNI,
                                                            ClientAuth clientAuth,
                                                            List<String> applicationProtocols,
                                                            boolean force,
                                                            ContextInternal ctx) {
    Future<SslContextProvider> f;
    boolean useWorker;
    f = buildConfig(sslOptions, force, ctx).map(config -> buildSslContextProvider(sslOptions,
      endpointIdentificationAlgorithm, supplier, clientAuth, applicationProtocols, config));
    useWorker = useWorkerPool;
    return f.map(c -> new SslChannelProvider(
      c,
      useSNI,
      ctx.owner().getInternalWorkerPool().executor(),
      useWorker));
  }

  private static TrustOptions trustOptionsOf(SSLOptions sslOptions) {
    if (sslOptions instanceof ClientSSLOptions) {
      ClientSSLOptions clientSSLOptions = (ClientSSLOptions) sslOptions;
      if (clientSSLOptions.isTrustAll()) {
        return TrustAllOptions.INSTANCE;
      }
    }
    return sslOptions.getTrustOptions();
  }

  private Future<Config> buildConfig(SSLOptions sslOptions, boolean force, ContextInternal ctx) {
    if (trustOptionsOf(sslOptions) == null && sslOptions.getKeyCertOptions() == null) {
      return ctx.succeededFuture(NULL_CONFIG);
    }
    Promise<Config> promise;
    ConfigKey k = new ConfigKey(sslOptions);
    synchronized (this) {
      if (force) {
        configMap.remove(k);
      } else {
        Future<Config> fut = configMap.get(k);
        if (fut != null) {
          return fut;
        }
      }
      promise = Promise.promise();
      configMap.put(k, promise.future());
    }
    ctx.executeBlockingInternal(() -> {
      KeyManagerFactory keyManagerFactory = null;
      Function<String, KeyManagerFactory> keyManagerFactoryMapper = null;
      TrustManagerFactory trustManagerFactory = null;
      Function<String, TrustManager[]> trustManagerMapper = null;
      List<CRL> crls = new ArrayList<>();
      if (sslOptions.getKeyCertOptions() != null) {
        keyManagerFactory = sslOptions.getKeyCertOptions().getKeyManagerFactory(ctx.owner());
        keyManagerFactoryMapper = sslOptions.getKeyCertOptions().keyManagerFactoryMapper(ctx.owner());
      }
      TrustOptions trustOptions = trustOptionsOf(sslOptions);
      if (trustOptions != null) {
        trustManagerFactory = trustOptions.getTrustManagerFactory(ctx.owner());
        trustManagerMapper = trustOptions.trustManagerMapper(ctx.owner());
      }
      List<Buffer> tmp = new ArrayList<>();
      if (sslOptions.getCrlPaths() != null) {
        tmp.addAll(sslOptions.getCrlPaths()
          .stream()
          .map(path -> ctx.owner().resolveFile(path).getAbsolutePath())
          .map(ctx.owner().fileSystem()::readFileBlocking)
          .collect(Collectors.toList()));
      }
      if (sslOptions.getCrlValues() != null) {
        tmp.addAll(sslOptions.getCrlValues());
      }
      CertificateFactory certificatefactory = CertificateFactory.getInstance("X.509");
      for (Buffer crlValue : tmp) {
        crls.addAll(certificatefactory.generateCRLs(new ByteArrayInputStream(crlValue.getBytes())));
      }
      return new Config(keyManagerFactory, trustManagerFactory, keyManagerFactoryMapper, trustManagerMapper, crls);
    }).onComplete(promise);
    return promise.future();
  }

  private static class LruCache<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    public LruCache(int maxSize) {
      if (maxSize < 1) {
        throw new UnsupportedOperationException();
      }
      this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() > maxSize;
    }
  }

  private final static class ConfigKey {
    private final KeyCertOptions keyCertOptions;
    private final TrustOptions trustOptions;
    private final List<Buffer> crlValues;
    public ConfigKey(SSLOptions options) {
      this(options.getKeyCertOptions(), trustOptionsOf(options), options.getCrlValues());
    }
    public ConfigKey(KeyCertOptions keyCertOptions, TrustOptions trustOptions, List<Buffer> crlValues) {
      this.keyCertOptions = keyCertOptions;
      this.trustOptions = trustOptions;
      this.crlValues = crlValues != null ? new ArrayList<>(crlValues) : null;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof ConfigKey) {
        ConfigKey that = (ConfigKey) obj;
        return Objects.equals(keyCertOptions, that.keyCertOptions) && Objects.equals(trustOptions, that.trustOptions) && Objects.equals(crlValues, that.crlValues);
      }
      return false;
    }

    @Override
    public int hashCode() {
      int hashCode = Objects.hashCode(keyCertOptions);
      hashCode = 31 * hashCode + Objects.hashCode(trustOptions);
      hashCode = 31 * hashCode + Objects.hashCode(crlValues);
      return hashCode;
    }
  }

  private final static class Config {
    private final KeyManagerFactory keyManagerFactory;
    private final TrustManagerFactory trustManagerFactory;
    private final Function<String, KeyManagerFactory> keyManagerFactoryMapper;
    private final Function<String, TrustManager[]> trustManagerMapper;
    private final List<CRL> crls;
    public Config(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory, Function<String, KeyManagerFactory> keyManagerFactoryMapper, Function<String, TrustManager[]> trustManagerMapper, List<CRL> crls) {
      this.keyManagerFactory = keyManagerFactory;
      this.trustManagerFactory = trustManagerFactory;
      this.keyManagerFactoryMapper = keyManagerFactoryMapper;
      this.trustManagerMapper = trustManagerMapper;
      this.crls = crls;
    }
  }
}
