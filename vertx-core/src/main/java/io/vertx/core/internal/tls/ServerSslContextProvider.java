/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.cert.CRL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerSslContextProvider extends SslContextProvider {

  private final ClientAuth clientAuth;

  public ServerSslContextProvider(boolean useWorkerPool, ClientAuth clientAuth, Set<String> enabledCipherSuites, Set<String> enabledProtocols, KeyManagerFactory keyManagerFactory, Function<String, KeyManagerFactory> keyManagerFactoryMapper, TrustManagerFactory trustManagerFactory, Function<String, TrustManager[]> trustManagerMapper, List<CRL> crls, Supplier<SslContextFactory> provider) {
    super(useWorkerPool, enabledCipherSuites, enabledProtocols, keyManagerFactory, keyManagerFactoryMapper, trustManagerFactory, trustManagerMapper, crls, provider);

    this.clientAuth = clientAuth;
  }

  private SslContext sslServerContext(String serverName, List<String> applicationProtocols) throws Exception {
    return sslContext(serverName, applicationProtocols, true);
  }

  public SslContext sslServerContext(List<String> applicationProtocols) {
    try {
      return sslServerContext(null, applicationProtocols);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public Mapping<? super String, ? extends SslContext> serverNameMapping(List<String> applicationProtocols) {
    return (Mapping<String, SslContext>) serverName -> {
      try {
        return sslServerContext(serverName, applicationProtocols);
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
          sslContext = sslServerContext(serverName, applicationProtocols);
        } catch (Exception e) {
          promise.setFailure(e);
          return;
        }
        promise.setSuccess(sslContext);
      });
      return promise;
    };
  }

  @Override
  protected SslContext createContext(KeyManagerFactory keyManagerFactory, TrustManager[] trustManagers, String serverName, List<String> applicationProtocols) {
    return createServerContext(keyManagerFactory, trustManagers, applicationProtocols);
  }

  public SslContext createServerContext(List<String> applicationProtocols) {
    return createServerContext(defaultKeyManagerFactory(), defaultTrustManagers(), applicationProtocols);
  }

  public SslContext createServerContext(KeyManagerFactory keyManagerFactory,
                                        TrustManager[] trustManagers,
                                        List<String> applicationProtocols) {
    if (keyManagerFactory == null) {
      keyManagerFactory = defaultKeyManagerFactory();
    }
    if (trustManagers == null) {
      trustManagers = defaultTrustManagers();
    }
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
}
