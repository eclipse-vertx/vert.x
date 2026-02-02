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
 * <p>Configuration of a Quic client.</p>
 *
 * <p>The default transport configuration, allows the client to open bidi streams to a server with sensitive defaults values,
 * it does not allow to open uni streams nor allows the server to open streams toward the client.</p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicClientConfig extends QuicEndpointConfig {

  /**
   * The default value of connect timeout = 60 seconds
   */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(60);

  private Duration connectTimeout;
  private SocketAddress localAddress;

  public QuicClientConfig() {

    configureClient(getTransportConfig());

    this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    this.localAddress = null;
  }

  public QuicClientConfig(QuicClientConfig other) {
    super(other);

    this.connectTimeout = other.connectTimeout;
    this.localAddress = other.localAddress;
  }

  private static void configureClient(QuicConfig cfg) {
    cfg.setInitialMaxData(QuicConfig.DEFAULT_CLIENT_MAX_INITIAL_DATA);
    cfg.setInitialMaxStreamDataBidiLocal(QuicConfig.DEFAULT_CLIENT_MAX_STREAM_DATA_BIDI_LOCAL);
    cfg.setInitialMaxStreamDataBidiRemote(QuicConfig.DEFAULT_CLIENT_MAX_STREAM_DATA_BIDI_REMOTE);
    cfg.setInitialMaxStreamDataUni(QuicConfig.DEFAULT_CLIENT_MAX_STREAMS_DATA_UNI);
    cfg.setInitialMaxStreamsBidi(QuicConfig.DEFAULT_CLIENT_MAX_STREAMS_DATA_BIDI);
    cfg.setInitialMaxStreamsUni(QuicConfig.DEFAULT_CLIENT_MAX_STREAM_DATA_UNI);
    cfg.setDisableActiveMigration(QuicConfig.DEFAULT_CLIENT_DISABLE_ACTIVE_MIGRATION);
  }

  @Override
  public QuicClientConfig setTransportConfig(QuicConfig transportConfig) {
    return (QuicClientConfig) super.setTransportConfig(transportConfig);
  }

  @Override
  public QuicClientConfig setQLogConfig(QLogConfig qLogConfig) {
    return (QuicClientConfig) super.setQLogConfig(qLogConfig);
  }

  @Override
  public QuicClientConfig setKeyLogFile(String keyLogFile) {
    return (QuicClientConfig) super.setKeyLogFile(keyLogFile);
  }

  @Override
  public QuicClientConfig setStreamIdleTimeout(Duration idleTimeout) {
    return (QuicClientConfig) super.setStreamIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientConfig setStreamReadIdleTimeout(Duration idleTimeout) {
    return (QuicClientConfig) super.setStreamReadIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientConfig setStreamWriteIdleTimeout(Duration idleTimeout) {
    return (QuicClientConfig) super.setStreamWriteIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientConfig setMetricsName(String metricsName) {
    return (QuicClientConfig) super.setMetricsName(metricsName);
  }

  @Override
  public QuicClientConfig setStreamLogging(NetworkLogging config) {
    return (QuicClientConfig) super.setStreamLogging(config);
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
  public QuicClientConfig setConnectTimeout(Duration connectTimeout) {
    if (connectTimeout.isNegative()) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }

  /**
   * @return the local address to bind for network connections.
   */
  public SocketAddress getLocalAddress() {
    return localAddress;
  }

  /**
   * Set the local address to bind for network connections. When the local address is null,
   * it will pick any local address, the default local address is null.
   *
   * @param localAddress the local address
   * @return a reference to this, so the API can be used fluently
   */
  public QuicClientConfig setLocalAddress(SocketAddress localAddress) {
    this.localAddress = localAddress;
    return this;
  }
}
