/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.spi.metrics.TCPMetrics;

/**
 * A builder to configure NetClient plugins.
 */
public class NetClientBuilder {

  private VertxInternal vertx;
  private CloseFuture closeFuture;
  private NetClientOptions options;
  private TCPMetrics metrics;

  public NetClientBuilder(VertxInternal vertx, NetClientOptions options) {
    this.vertx = vertx;
    this.options = options;
  }

  public NetClientBuilder closeFuture(CloseFuture closeFuture) {
    this.closeFuture = closeFuture;
    return this;
  }

  public NetClientBuilder metrics(TCPMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public NetClient build() {
    NetClientImpl client = new NetClientImpl(vertx, metrics, options, closeFuture);
    closeFuture.add(client);
    return client;
  }
}
