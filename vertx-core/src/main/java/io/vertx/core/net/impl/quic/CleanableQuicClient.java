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
package io.vertx.core.net.impl.quic;

import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.time.Duration;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableQuicClient extends QuicClientImpl implements Closeable {

  private final VertxInternal vertx;
  private ContextInternal listenContext;

  public CleanableQuicClient(VertxInternal vertx,
                             QuicClientConfig config,
                             ClientSSLOptions sslOptions) {
    super(vertx, config, sslOptions);
    this.vertx = vertx;
  }

  @Override
  public Future<Integer> bind(ContextInternal current, SocketAddress address) {
    synchronized (this) {
      if (listenContext != null) {
        return current.failedFuture(new IllegalStateException());
      }
      listenContext = current;
    }
    current.addCloseHook(this);
    return super
      .bind(current, address)
      .andThen(ar -> {
        if (ar.failed()) {
          synchronized (CleanableQuicClient.this) {
            if (listenContext == null) {
              return;
            }
            listenContext = null;
          }
          current.removeCloseHook(this);
        }
      });
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    ContextInternal context;
    synchronized (this) {
      if (listenContext == null) {
        return vertx.succeededFuture();
      }
      context = listenContext;
      listenContext = null;
    }
    context.removeCloseHook(this);
    return super.shutdown(timeout);
  }

  @Override
  public void close(Completable<Void> completion) {
    close().onComplete(completion);
  }
}
