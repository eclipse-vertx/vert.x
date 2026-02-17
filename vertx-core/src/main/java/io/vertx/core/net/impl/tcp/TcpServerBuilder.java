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
import io.vertx.core.internal.net.TcpServerInternal;
import io.vertx.core.net.*;

/**
 * A builder to configure a TcpServer.
 */
public class TcpServerBuilder {

  private VertxInternal vertx;
  private TcpServerConfig config;
  private ServerSSLOptions sslOptions;
  private String protocol;
  private boolean fileRegionEnabled;
  private boolean registerWriteHandler;
  private boolean cleanable;

  public TcpServerBuilder(VertxInternal vertx, TcpServerConfig config) {
    this.vertx = vertx;
    this.config = config;
    this.protocol = null;
    this.fileRegionEnabled = false;
    this.registerWriteHandler = false;
    this.cleanable = false;
  }

  public TcpServerBuilder sslOptions(ServerSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  public TcpServerBuilder cleanable(boolean cleanable) {
    this.cleanable = cleanable;
    return this;
  }

  public TcpServerBuilder fileRegionEnabled(boolean fileRegionEnabled) {
    this.fileRegionEnabled = fileRegionEnabled;
    return this;
  }

  public TcpServerBuilder protocol(String protocol) {
    this.protocol = protocol;
    return this;
  }

  public TcpServerBuilder registerWriteHandler(boolean registerWriteHandler) {
    this.registerWriteHandler = registerWriteHandler;
    return this;
  }

  public TcpServerInternal build() {
    TcpServerImpl server;
    if (cleanable) {
      server = new CleanableTcpServer(vertx,
        config,
        protocol,
        sslOptions,
        fileRegionEnabled,
        registerWriteHandler);
    } else {
      server = new TcpServerImpl(
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
