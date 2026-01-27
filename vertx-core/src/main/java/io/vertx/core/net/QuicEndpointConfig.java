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
import java.util.Objects;

/**
 * Configuration of a Quic client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public abstract class QuicEndpointConfig {

  private QuicOptions transportOptions;
  private SSLOptions sslOptions;
  private QLogConfig qlogConfig;
  private String keyLogFile;
  private Duration streamIdleTimeout;
  private Duration streamReadIdleTimeout;
  private Duration streamWriteIdleTimeout;
  private NetworkLogging streamLogging;

  public QuicEndpointConfig() {
    this.transportOptions = new QuicOptions();
  }

  public QuicEndpointConfig(QuicEndpointConfig other) {

    QLogConfig qLogConfig = other.qlogConfig;

    this.transportOptions = other.transportOptions.copy();
    this.sslOptions = other.sslOptions.copy();
    this.qlogConfig = qLogConfig != null ? new QLogConfig(qLogConfig) : null;
    this.keyLogFile = other.keyLogFile;
    this.streamIdleTimeout = other.streamIdleTimeout;
    this.streamReadIdleTimeout = other.streamReadIdleTimeout;
    this.streamWriteIdleTimeout = other.streamWriteIdleTimeout;
    this.streamLogging = other.streamLogging != null ? new NetworkLogging(other.streamLogging) : null;
  }

  /**
   * @return the endpoint transport options
   */
  public QuicOptions getTransportOptions() {
    return transportOptions;
  }

  public QuicEndpointConfig setTransportOptions(QuicOptions transportOptions) {
    this.transportOptions = Objects.requireNonNull(transportOptions);
    return this;
  }

  /**
   * @return the endpoint SSL options
   */
  public SSLOptions getSslOptions() {
    SSLOptions opts = sslOptions;
    if (opts == null) {
      opts = getOrCreateSSLOptions();
      sslOptions = opts;
    }
    return opts;
  }

  protected void setSslOptions(SSLOptions sslOptions)  {
    this.sslOptions = sslOptions;
  }

  protected abstract SSLOptions getOrCreateSSLOptions();

  /**
   * @return the endpoint QLog config.
   */
  public QLogConfig getQLogConfig() {
    return qlogConfig;
  }

  /**
   * <p>Set the endpoint QLog config.</p>
   *
   * <p>The config can point to a single file or to a directory where qlog files will be created.</p>
   *
   * @param qLogConfig the qlog config
   * @return this exact object instance
   */
  public QuicEndpointConfig setQLogConfig(QLogConfig qLogConfig) {
    this.qlogConfig = qLogConfig;
    return this;
  }

  /**
   * @return the path of the configured key log file or {@code null} (default).
   */
  public String getKeyLogFile() {
    return keyLogFile;
  }

  /**
   * <p>Configures the endpoint to dump the cryptographic secrets using in TLS in the
   * <a href="https://www.ietf.org/archive/id/draft-thomson-tls-keylogfile-00.html">{@code SSLKEYLOGFILE}</a> format.</p>
   *
   * <p>The file might exist or will be created (in which case the parent file must exist), content will be appended
   * to the file.</p>
   *
   * <p>This should be used only for debugging purpose and must not be used in production. This feature is disabled
   * by default.</p>
   *
   * @param keyLogFile the path to the key log file
   * @return this exact object instance
   */
  public QuicEndpointConfig setKeyLogFile(String keyLogFile) {
    this.keyLogFile = keyLogFile;
    return this;
  }

  /**
   * Set the stream idle timeout, {@code null} means don't time out.
   * This determines if a stream will timeout and be closed if no data is received nor sent within the timeout.
   *
   * @param idleTimeout  the idle timeout
   * @return a reference to this, so the API can be used fluently
   */
  public QuicEndpointConfig setStreamIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("streamIdleTimeout must be >= 0");
    }
    this.streamIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the idle timeout applied to each stream
   */
  public Duration getStreamIdleTimeout() {
    return streamIdleTimeout;
  }

  /**
   * Set the stream read idle timeout, {@code null} means don't time out.
   * This determines if a stream will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout  the read idle timeout
   * @return a reference to this, so the API can be used fluently
   */
  public QuicEndpointConfig setStreamReadIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("streamReadIdleTimeout must be >= 0");
    }
    this.streamReadIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the read idle timeout applied to each stream
   */
  public Duration getStreamReadIdleTimeout() {
    return streamReadIdleTimeout;
  }

  /**
   * Set the stream write idle timeout, {@code null} means don't time out.
   * This determines if a stream will timeout and be closed if no data is sent within the timeout.
   *
   * @param idleTimeout  the write idle timeout
   * @return a reference to this, so the API can be used fluently
   */
  public QuicEndpointConfig setStreamWriteIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("streamWriteIdleTimeout must be >= 0");
    }
    this.streamWriteIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the write idle timeout applied to each stream
   */
  public Duration getStreamWriteIdleTimeout() {
    return streamWriteIdleTimeout;
  }

  /**
   * @return the stream network logging config, {@code null} means disabled
   */
  public NetworkLogging getStreamLogging() {
    return streamLogging;
  }

  /**
   * Configure the per stream networking logging: Netty's stream pipeline is configured for logging on Netty's logger.
   *
   * @param config the stream network logging config, {@code null} means disabled
   * @return a reference to this, so the API can be used fluently
   */
  public QuicEndpointConfig setStreamLogging(NetworkLogging config) {
    this.streamLogging = config;
    return this;
  }
}
