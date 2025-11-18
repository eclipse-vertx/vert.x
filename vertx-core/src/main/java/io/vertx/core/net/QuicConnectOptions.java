/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;

/**
 * Options for configuring how to connect to a QUIC server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicConnectOptions {

  private ClientSSLOptions sslOptions;
  private QLogConfig qlogConfig;
  private Duration timeout;

  public QuicConnectOptions() {
  }

  public QuicConnectOptions(QuicConnectOptions that) {

    ClientSSLOptions sslOptions = that.sslOptions;
    if (sslOptions != null) {
      sslOptions = sslOptions.copy();
    }
    QLogConfig qlogConfig = that.qlogConfig;
    if (qlogConfig != null) {
      qlogConfig = new QLogConfig(qlogConfig);
    }
    Duration timeout = that.timeout;

    this.sslOptions = sslOptions;
    this.timeout = timeout;
    this.qlogConfig = qlogConfig;
  }

  /**
   * @return the SSL options overriding the client ssl options
   */
  public ClientSSLOptions getSslOptions() {
    return sslOptions;
  }

  /**
   * Set the SSL to connect with, if none is set, the client one is used.
   *
   * @param sslOptions ssl options
   * @return a reference to this, so the API can be used fluently
   */
  public QuicConnectOptions setSslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  /**
   * @return the QLog config overriding the client config
   */
  public QLogConfig getQLogConfig() {
    return qlogConfig;
  }

  /**
   * Set a QLof config to connect with, if none is set, the client one is used.
   *
   * @param config the config
   * @return a reference to this, so the API can be used fluently
   */
  public QuicConnectOptions setQLogConfig(QLogConfig config) {
    this.qlogConfig = config;
    return this;
  }

  /**
   * @return the connect timeout duration
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Override the client connect timeout.
   *
   * @param timeout connect timeout
   * @return a reference to this, so the API can be used fluently
   */
  public QuicConnectOptions setTimeout(Duration timeout) {
    if (timeout != null && timeout.isNegative()) {
      throw new IllegalArgumentException("Timeout must be >= 0");
    }
    this.timeout = timeout;
    return this;
  }
}
