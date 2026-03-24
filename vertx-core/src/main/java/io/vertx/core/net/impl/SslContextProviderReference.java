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

import io.vertx.core.Future;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.ServerSslContextManager;
import io.vertx.core.internal.tls.ServerSslContextProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.ServerSSLOptions;

/**
 * Takes care of handling SSL options updates validating the options before
 * applying the update.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslContextProviderReference {

  private final ServerSslContextManager sslContextManager;
  private volatile ServerSslContextProvider sslContextProvider;
  private Future<ServerSslContextProvider> updateInProgress;

  public SslContextProviderReference(ServerSslContextManager sslContextManager) {
    this.sslContextManager = sslContextManager;
  }

  /**
   * @return the most recent {@link SslContextProvider} version.
   */
  public ServerSslContextProvider get() {
    return sslContextProvider;
  }

  /**
   * Like {@link #computeUpdate(ServerSSLOptions, ContextInternal, boolean)} with {@code force = false}.
   */
  public Future<ServerSslContextProvider> update(ServerSSLOptions options, ContextInternal ctx) {
    return update(options, ctx, false);
  }

  /**
   * Apply an {@code update} to the SSL context, the update will be applied after updates in progress have completed.
   *
   * @param update the update
   * @param ctx the vertx context
   * @param force force the update when options are equals
   * @return a future signaling the update success
   */
  public Future<ServerSslContextProvider> update(ServerSSLOptions update, ContextInternal ctx, boolean force) {
    Future<ServerSslContextProvider> fut;
    synchronized (this) {
      if (updateInProgress == null) {
        fut = computeUpdate(update, ctx, force);
      } else {
        fut = updateInProgress.mapEmpty().transform(ar -> computeUpdate(update, ctx, force));
      }
      updateInProgress = fut;
    }
    return fut;
  }

  private Future<ServerSslContextProvider> computeUpdate(ServerSSLOptions options, ContextInternal ctx, boolean force) {
    ServerSSLOptions sslOptions = options.copy();
    Future<ServerSslContextProvider> res = sslContextManager.resolveSslContextProvider(
      sslOptions,
      force,
      ctx);
    return res.map(update -> {
      boolean updated;
      synchronized (SslContextProviderReference.this) {
        updated = sslContextProvider != update;
        sslContextProvider = update;
      }
      return updated ? update : null;
    });
  }
}
