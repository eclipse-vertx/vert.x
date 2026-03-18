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

  /**
   * The default value for reconnect attempts = 0
   */
  public static final int DEFAULT_RECONNECT_ATTEMPTS = 0;

  /**
   * The default value for reconnect interval = 1000 ms
   */
  public static final Duration DEFAULT_RECONNECT_INTERVAL = Duration.ofSeconds(1);

  private Duration connectTimeout;
  private SocketAddress localAddress;
  private int reconnectAttempts;
  private Duration reconnectInterval;

  public QuicClientConfig() {

    configureClient(getTransportConfig());

    this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    this.localAddress = null;
    this.reconnectAttempts = DEFAULT_RECONNECT_ATTEMPTS;
    this.reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
  }

  public QuicClientConfig(QuicClientConfig other) {
    super(other);

    this.connectTimeout = other.connectTimeout;
    this.localAddress = other.localAddress;
    this.reconnectAttempts = other.reconnectAttempts;
    this.reconnectInterval = other.reconnectInterval;
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
  public QuicClientConfig setIdleTimeout(Duration idleTimeout) {
    return (QuicClientConfig) super.setIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientConfig setReadIdleTimeout(Duration idleTimeout) {
    return (QuicClientConfig) super.setReadIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientConfig setWriteIdleTimeout(Duration idleTimeout) {
    return (QuicClientConfig) super.setWriteIdleTimeout(idleTimeout);
  }

  @Override
  public QuicClientConfig setMetricsName(String metricsName) {
    return (QuicClientConfig) super.setMetricsName(metricsName);
  }

  @Override
  public QuicClientConfig setMaxStreamBidiRequests(int maxStreamRequests) {
    return (QuicClientConfig) super.setMaxStreamBidiRequests(maxStreamRequests);
  }

  @Override
  public QuicClientConfig setMaxStreamUniRequests(int maxStreamRequests) {
    return (QuicClientConfig) super.setMaxStreamUniRequests(maxStreamRequests);
  }

  @Override
  public QuicClientConfig setLogConfig(LogConfig config) {
    return (QuicClientConfig) super.setLogConfig(config);
  }

  @Override
  public QuicServerConfig setReuseAddress(boolean reuseAddress) {
    return (QuicServerConfig) super.setReuseAddress(reuseAddress);
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

  /**
   * @return  the value of reconnect attempts
   */
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  /**
   * Set the value of reconnect attempts
   *
   * @param attempts  the maximum number of reconnect attempts
   * @return a reference to this, so the API can be used fluently
   */
  public QuicClientConfig setReconnectAttempts(int attempts) {
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  /**
   * @return  the value of reconnect interval
   */
  public Duration getReconnectInterval() {
    return reconnectInterval;
  }

  /**
   * Set the reconnect interval
   *
   * @param interval  the reconnect interval
   * @return a reference to this, so the API can be used fluently
   */
  public QuicClientConfig setReconnectInterval(Duration interval) {
    if (interval.isNegative() || interval.isZero()) {
      throw new IllegalArgumentException("reconnect interval must be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }
}
