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

import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.SSLEngineOptions;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientSslContextManager extends SslContextManager<ClientSslContextProvider> {

  public ClientSslContextManager(SSLEngineOptions sslEngineOptions, int cacheMaxSize) {
    super(sslEngineOptions, cacheMaxSize);
  }

  public ClientSslContextManager(SSLEngineOptions sslEngineOptions) {
    super(sslEngineOptions);
  }

  public Future<ClientSslContextProvider> resolveSslContextProvider(ClientSSLOptions options, ContextInternal ctx) {
    String hostnameVerificationAlgorithm = options.getHostnameVerificationAlgorithm();
    if (hostnameVerificationAlgorithm == null) {
      return ctx.failedFuture("Missing hostname verification algorithm");
    }
    Function<Config, ClientSslContextProvider> factory = new Function<Config, ClientSslContextProvider>() {
      @Override
      public ClientSslContextProvider apply(Config config) {
        return new ClientSslContextProvider(
          useWorkerPool,
          hostnameVerificationAlgorithm,
          options.getEnabledCipherSuites(),
          options.getEnabledSecureTransportProtocols(),
          config.keyManagerFactory,
          config.keyManagerFactoryMapper,
          config.trustManagerFactory,
          config.trustManagerMapper,
          config.crls,
          supplier);
      }
    };
    return resolveSslContextProvider(options, factory, false, ctx);
  }
}
