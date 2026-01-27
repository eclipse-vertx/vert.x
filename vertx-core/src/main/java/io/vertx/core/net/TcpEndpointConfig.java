/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.netty.handler.logging.ByteBufFormat;

import java.time.Duration;

/**
 * Should this be {@code TcpConfig} instead ?
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TcpEndpointConfig {

  private TcpOptions transportOptions;
  private SSLOptions sslOptions;
  private SSLEngineOptions sslEngineOptions;
  private Duration idleTimeout;
  private Duration readIdleTimeout;
  private Duration writeIdleTimeout;
  private boolean logActivity;
  private ByteBufFormat activityLogDataFormat;
  private boolean ssl;

  public TcpEndpointConfig() {
    this.transportOptions = new TcpOptions();
    this.sslOptions = null;
    this.sslEngineOptions = TCPSSLOptions.DEFAULT_SSL_ENGINE;
    this.idleTimeout = null;
    this.readIdleTimeout = null;
    this.writeIdleTimeout = null;
    this.logActivity = NetworkOptions.DEFAULT_LOG_ENABLED;
    this.activityLogDataFormat = NetworkOptions.DEFAULT_LOG_ACTIVITY_FORMAT;
    this.ssl = TCPSSLOptions.DEFAULT_SSL;
  }

  public TcpEndpointConfig(TcpEndpointConfig other) {
    this.transportOptions = other.transportOptions != null ? new TcpOptions(other.transportOptions) : null;
    this.sslOptions = other.sslOptions != null ? other.sslOptions.copy() : null;
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.copy() : null;
    this.idleTimeout = other.idleTimeout;
    this.readIdleTimeout = other.readIdleTimeout;
    this.writeIdleTimeout = other.writeIdleTimeout;
    this.logActivity = other.logActivity;
    this.activityLogDataFormat = other.activityLogDataFormat;
    this.ssl = other.ssl;
  }

  public TcpEndpointConfig(TCPSSLOptions options) {
    setTransportOptions(new TcpOptions(options.getTransportOptions()));
    setSslOptions(options.getSslOptions() != null ? options.getSslOptions().copy() : null);
    setSslEngineOptions(options.getSslEngineOptions() != null ? options.getSslEngineOptions().copy() : null);
    setIdleTimeout(Duration.of(options.getIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    setReadIdleTimeout(Duration.of(options.getReadIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    setWriteIdleTimeout(Duration.of(options.getWriteIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    setLogActivity(options.getLogActivity());
    setActivityLogDataFormat(options.getActivityLogDataFormat());
    setSsl(options.isSsl());
  }

  /**
   * @return the client TCP transport options
   */
  public TcpOptions getTransportOptions() {
    return transportOptions;
  }

  /**
   * Set the client TCP transport options.
   *
   * @param transportOptions the transport options
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setTransportOptions(TcpOptions transportOptions) {
    this.transportOptions = transportOptions;
    return this;
  }

  /**
   * @return the client SSL options.
   */
  public SSLOptions getSslOptions() {
    return sslOptions;
  }

  /**
   * Set the SSL options.
   *
   * @param sslOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  protected TcpEndpointConfig setSslOptions(SSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  /**
   * @return the SSL engine implementation to use
   */
  public SSLEngineOptions getSslEngineOptions() {
    return sslEngineOptions;
  }

  /**
   * Set to use SSL engine implementation to use.
   *
   * @param sslEngineOptions the ssl engine to use
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    this.sslEngineOptions = sslEngineOptions;
    return this;
  }

  /**
   * @return the idle timeout
   */
  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Set the idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received nor sent within the timeout.
   *
   * @param idleTimeout  the timeout
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  /**
   * Set the read idle timeout. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout  the read timeout
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setReadIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("readIdleTimeout must be >= 0");
    }
    this.readIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the read idle timeout
   */
  public Duration getReadIdleTimeout() {
    return readIdleTimeout;
  }

  /**
   * Set the write idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is sent within the timeout.
   *
   * @param idleTimeout  the write timeout
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setWriteIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("writeIdleTimeout must be >= 0");
    }
    this.writeIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the write idle timeout.
   */
  public Duration getWriteIdleTimeout() {
    return writeIdleTimeout;
  }

  /**
   * @return true when network activity logging is enabled
   */
  public boolean getLogActivity() {
    return logActivity;
  }

  /**
   * Set to true to enabled network activity logging: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param logActivity true for logging the network activity
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setLogActivity(boolean logActivity) {
    this.logActivity = logActivity;
    return this;
  }

  /**
   * @return Netty's logging handler's data format.
   */
  public ByteBufFormat getActivityLogDataFormat() {
    return activityLogDataFormat;
  }

  /**
   * Set the value of Netty's logging handler's data format: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param activityLogDataFormat the format to use
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    this.activityLogDataFormat = activityLogDataFormat;
    return this;
  }

  /**
   *
   * @return is SSL/TLS enabled?
   */
  public boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }
}
