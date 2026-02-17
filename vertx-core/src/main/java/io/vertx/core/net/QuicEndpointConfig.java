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
public abstract class QuicEndpointConfig extends EndpointConfig {

  private QLogConfig qlogConfig;
  private String keyLogFile;

  public QuicEndpointConfig() {
    super();
    setTransportConfig(new QuicConfig());
    this.qlogConfig = null;
  }

  public QuicEndpointConfig(QuicEndpointConfig other) {
    super(other);

    QLogConfig qLogConfig = other.qlogConfig;

    this.qlogConfig = qLogConfig != null ? new QLogConfig(qLogConfig) : null;
    this.keyLogFile = other.keyLogFile;
  }

  /**
   * @return the endpoint transport config
   */
  public QuicConfig getTransportConfig() {
    return (QuicConfig)super.getTransportConfig();
  }

  public QuicEndpointConfig setTransportConfig(QuicConfig transportConfig) {
    return (QuicEndpointConfig)super.setTransportConfig(transportConfig);
  }

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

  public QuicEndpointConfig setIdleTimeout(Duration idleTimeout) {
    return (QuicEndpointConfig) super.setIdleTimeout(idleTimeout);
  }

  public QuicEndpointConfig setReadIdleTimeout(Duration idleTimeout) {
    return (QuicEndpointConfig) super.setReadIdleTimeout(idleTimeout);
  }

  public QuicEndpointConfig setWriteIdleTimeout(Duration idleTimeout) {
    return (QuicEndpointConfig) super.setWriteIdleTimeout(idleTimeout);
  }

  public QuicEndpointConfig setMetricsName(String metricsName) {
    return (QuicEndpointConfig) super.setMetricsName(metricsName);
  }

  public QuicEndpointConfig setNetworkLogging(NetworkLogging config) {
    return (QuicEndpointConfig) super.setNetworkLogging(config);
  }
}
