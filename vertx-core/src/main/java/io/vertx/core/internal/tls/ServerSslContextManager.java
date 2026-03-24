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
import io.vertx.core.http.ClientAuth;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.ServerSSLOptions;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerSslContextManager extends SslContextManager<ServerSslContextProvider> {

  public ServerSslContextManager(SSLEngineOptions sslEngineOptions, int cacheMaxSize) {
    super(sslEngineOptions, cacheMaxSize);
  }

  public ServerSslContextManager(SSLEngineOptions sslEngineOptions) {
    super(sslEngineOptions);
  }

  public Future<ServerSslContextProvider> resolveSslContextProvider(ServerSSLOptions options, ContextInternal ctx) {
    return resolveSslContextProvider(options, false, ctx);
  }

  public Future<ServerSslContextProvider> resolveSslContextProvider(ServerSSLOptions options, boolean force, ContextInternal ctx) {
    ClientAuth clientAuth = options.getClientAuth();
    if (clientAuth == null) {
      clientAuth = ClientAuth.NONE;
    }
    ClientAuth c = clientAuth;
    Function<Config, ServerSslContextProvider> factory = new Function<Config, ServerSslContextProvider>() {
      @Override
      public ServerSslContextProvider apply(Config config) {
        return new ServerSslContextProvider(
          useWorkerPool,
          c,
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
    return resolveSslContextProvider(options, factory, force, ctx);
  }

}
