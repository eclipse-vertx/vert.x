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
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.function.BiFunction;

/**
 * A builder to configure NetServer plugins.
 */
public class NetServerBuilder {

  private VertxInternal vertx;
  private TcpServerConfig config;
  private ServerSSLOptions sslOptions;
  private String protocol;
  private boolean fileRegionEnabled;
  private boolean registerWriteHandler;
  private boolean cleanable;

  public NetServerBuilder(VertxInternal vertx, TcpServerConfig config, ServerSSLOptions sslOptions) {
    this.vertx = vertx;
    this.config = config;
    this.sslOptions = sslOptions;
    this.protocol = null;
    this.fileRegionEnabled = false;
    this.registerWriteHandler = false;
    this.cleanable = false;
  }

  public NetServerBuilder(VertxInternal vertx, NetServerOptions options) {

    TcpServerConfig cfg = new TcpServerConfig(options);

    this.vertx = vertx;
    this.config = cfg;
    this.sslOptions = options.getSslOptions();
    this.fileRegionEnabled = options.isFileRegionEnabled();
    this.registerWriteHandler = options.isRegisterWriteHandler();
    this.cleanable = true;
  }

  public NetServerBuilder cleanable(boolean cleanable) {
    this.cleanable = cleanable;
    return this;
  }

  public NetServerBuilder fileRegionEnabled(boolean fileRegionEnabled) {
    this.fileRegionEnabled = fileRegionEnabled;
    return this;
  }

  public NetServerBuilder protocol(String protocol) {
    this.protocol = protocol;
    return this;
  }

  public NetServerInternal build() {
    NetServerInternal server;
    if (cleanable) {
      server = new CleanableNetServer(vertx,
        config,
        protocol,
        sslOptions,
        fileRegionEnabled,
        registerWriteHandler);
    } else {
      server = new NetServerImpl(
        vertx,
        config,
        protocol,
        sslOptions,
        fileRegionEnabled,
        registerWriteHandler
      );
    }
    return server;
  }
}
