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
 * Config operations of a Quic client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicClientOptions extends QuicEndpointOptions {

  /**
   * The default value of connect timeout = 60 seconds
   */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(60);

  private Duration connectTimeout;

  public QuicClientOptions() {
    this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
  }

  public QuicClientOptions(QuicClientOptions other) {
    super(other);

    this.connectTimeout = other.connectTimeout;
  }

  @Override
  public QuicClientOptions setQLogConfig(QLogConfig qLogConfig) {
    return (QuicClientOptions) super.setQLogConfig(qLogConfig);
  }

  @Override
  public QuicClientOptions setKeyLogFile(String keyLogFile) {
    return (QuicClientOptions) super.setKeyLogFile(keyLogFile);
  }

  @Override
  public QuicClientOptions setStreamIdleTimeout(Duration idleTimeout) {
    return (QuicClientOptions) super.setStreamIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientOptions setStreamReadIdleTimeout(Duration idleTimeout) {
    return (QuicClientOptions) super.setStreamReadIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientOptions setStreamWriteIdleTimeout(Duration idleTimeout) {
    return (QuicClientOptions) super.setStreamWriteIdleTimeout(idleTimeout);
  }

  @Override
  public ClientSSLOptions getSslOptions() {
    return (ClientSSLOptions) super.getSslOptions();
  }

  @Override
  protected ClientSSLOptions getOrCreateSSLOptions() {
    return new ClientSSLOptions();
  }

  /**
   * @return the value of connect timeout
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Set the connect timeout.
   *
   * @param connectTimeout  connect timeout
   * @return a reference to this, so the API can be used fluently
   */
  public QuicClientOptions setConnectTimeout(Duration connectTimeout) {
    if (connectTimeout.isNegative()) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }
}
