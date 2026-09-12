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

import io.vertx.core.Future;
import io.vertx.core.internal.ServiceResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;

import java.time.Duration;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableQuicClient extends QuicClientImpl {

  private final VertxInternal vertx;
  private final ServiceResource<SocketAddress, SocketAddress> serviceResource;

  public CleanableQuicClient(VertxInternal vertx,
                             QuicClientConfig config,
                             ClientSSLOptions sslOptions) {
    super(vertx, config, null, sslOptions);
    this.vertx = vertx;
    this.serviceResource = new ServiceResource<>() {
      @Override
      protected Future<SocketAddress> startImpl(ContextInternal context, SocketAddress args) {
        return CleanableQuicClient.super.bind(context, args);
      }
      @Override
      protected Future<?> stopImpl(ContextInternal context, SocketAddress args, Duration timeout) {
        return CleanableQuicClient.super.shutdown(timeout);
      }
    };
  }

  @Override
  public Future<SocketAddress> bind(ContextInternal current, SocketAddress address) {
    return serviceResource.start(current, address);
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return serviceResource.stop(vertx.getOrCreateContext(), timeout);
  }
}
