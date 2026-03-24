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

import io.netty.handler.ssl.SslContext;
import io.vertx.core.VertxException;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.cert.CRL;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientSslContextProvider extends SslContextProvider {

  private final String endpointIdentificationAlgorithm;

  public ClientSslContextProvider(boolean useWorkerPool, String endpointIdentificationAlgorithm, Set<String> enabledCipherSuites, Set<String> enabledProtocols, KeyManagerFactory keyManagerFactory, Function<String, KeyManagerFactory> keyManagerFactoryMapper, TrustManagerFactory trustManagerFactory, Function<String, TrustManager[]> trustManagerMapper, List<CRL> crls, Supplier<SslContextFactory> provider) {
    super(useWorkerPool, enabledCipherSuites, enabledProtocols, keyManagerFactory, keyManagerFactoryMapper, trustManagerFactory, trustManagerMapper, crls, provider);

    this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
  }

  public SslContext sslClientContext(String serverName, List<String> applicationProtocols) {
    try {
      return sslContext(serverName, applicationProtocols, false);
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  @Override
  protected SslContext createContext(KeyManagerFactory keyManagerFactory, TrustManager[] trustManagers, String serverName, List<String> applicationProtocols) {
    return createClientContext(keyManagerFactory, trustManagers, serverName == null ? null : new SNIHostName(serverName), applicationProtocols);
  }

  public SslContext createClientContext(List<String> applicationProtocols) {
    return createClientContext(defaultKeyManagerFactory(), defaultTrustManagers(), null, applicationProtocols);
  }

  public SslContext createClientContext(
    KeyManagerFactory keyManagerFactory,
    TrustManager[] trustManagers,
    SNIHostName serverName,
    List<String> applicationProtocols) {
    if (keyManagerFactory == null) {
      keyManagerFactory = defaultKeyManagerFactory();
    }
    if (trustManagers == null) {
      trustManagers = defaultTrustManagers();
    }
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
}
