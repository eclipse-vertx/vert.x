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
package io.vertx.core.net.impl.tcp;

import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.TransportMetrics;

/**
 * A builder to configure NetServer plugins.
 */
public class NetServerBuilder {

  private VertxInternal vertx;
  private TcpServerConfig config;
  private ServerSSLOptions sslOptions;
  private TransportMetrics<?> metrics;
  private boolean fileRegionEnabled;
  private boolean registerWriteHandler;

  public NetServerBuilder(VertxInternal vertx, TcpServerConfig config, ServerSSLOptions sslOptions) {
    this.vertx = vertx;
    this.config = config;
    this.sslOptions = sslOptions;
    this.fileRegionEnabled = false;
    this.registerWriteHandler = false;
    this.metrics = null;
  }

  public NetServerBuilder registerWriteHandler(boolean registerWriteHandler) {
    this.registerWriteHandler = registerWriteHandler;
    return this;
  }

  public NetServerBuilder fileRegionEnabled(boolean fileRegionEnabled) {
    this.fileRegionEnabled = fileRegionEnabled;
    return this;
  }

  public NetServerBuilder metrics(TransportMetrics<?> metricsProvider) {
    this.metrics = metricsProvider;
    return this;
  }

  public NetServerInternal build() {
    return new NetServerImpl(
      vertx,
      config,
      sslOptions,
      fileRegionEnabled,
      registerWriteHandler,
      metrics);
  }
}
