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
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.core.spi.metrics.TransportMetrics;

/**
 * A builder to configure NetClient plugins.
 */
public class NetClientBuilder {

  private VertxInternal vertx;
  private TcpClientConfig config;
  private ClientSSLOptions sslOptions;
  private TransportMetrics metrics;
  private String localAddress;
  private boolean registerWriteHandler;

  public NetClientBuilder(VertxInternal vertx, TcpClientConfig config, ClientSSLOptions sslOptions) {
    this.vertx = vertx;
    this.config = config;
    this.sslOptions = sslOptions;
    this.localAddress = null;
    this.registerWriteHandler = false;
  }

  public NetClientBuilder localAddress(String localAddress) {
    this.localAddress = localAddress;
    return this;
  }

  public NetClientBuilder registerWriteHandler(boolean registerWriteHandler) {
    this.registerWriteHandler = registerWriteHandler;
    return this;
  }

  public NetClientBuilder metrics(TransportMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public NetClientInternal build() {
    return new NetClientImpl(vertx, metrics, config, sslOptions, registerWriteHandler, localAddress);
  }
}
