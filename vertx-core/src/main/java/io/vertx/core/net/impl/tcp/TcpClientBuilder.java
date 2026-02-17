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
import io.vertx.core.internal.net.TcpClientInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.TcpClientConfig;

/**
 * A builder to configure TcpClient.
 */
public class TcpClientBuilder {

  private VertxInternal vertx;
  private TcpClientConfig config;
  private String protocol;
  private ClientSSLOptions sslOptions;
  private boolean registerWriteHandler;

  public TcpClientBuilder(VertxInternal vertx, TcpClientConfig config) {
    this.vertx = vertx;
    this.config = config;
    this.registerWriteHandler = false;
    this.protocol = null;
  }

  public TcpClientBuilder sslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  public TcpClientBuilder registerWriteHandler(boolean registerWriteHandler) {
    this.registerWriteHandler =  registerWriteHandler;
    return this;
  }

  public TcpClientBuilder protocol(String protocol) {
    this.protocol = protocol;
    return this;
  }

  public TcpClientInternal build() {
    return new TcpClientImpl(vertx, config, protocol, sslOptions, registerWriteHandler);
  }
}
