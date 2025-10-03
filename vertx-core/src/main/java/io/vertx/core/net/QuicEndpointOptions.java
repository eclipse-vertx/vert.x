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
import io.vertx.core.json.JsonObject;

/**
 * Config operations of a Quic endpoint.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public abstract class QuicEndpointOptions {

  private QuicOptions transportOptions;
  private SSLOptions sslOptions;
  private QLogConfig qLogConfig;

  public QuicEndpointOptions() {
    this.transportOptions = new QuicOptions();
  }

  public QuicEndpointOptions(QuicEndpointOptions other) {

    QLogConfig qLogConfig = other.qLogConfig;

    this.transportOptions = other.transportOptions.copy();
    this.sslOptions = other.sslOptions.copy();
    this.qLogConfig = qLogConfig != null ? new QLogConfig(qLogConfig) : null;
  }

  public QuicEndpointOptions(JsonObject json) {
    throw new UnsupportedOperationException();
  }

  /**
   * @return the endpoint transport options
   */
  public QuicOptions getTransportOptions() {
    return transportOptions;
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

  protected abstract SSLOptions getOrCreateSSLOptions();

  /**
   * @return the endpoint QLog config.
   */
  public QLogConfig getQLogConfig() {
    return qLogConfig;
  }

  /**
   * <p>Set the endpoint QLog config.</p>
   *
   * <p>The config can point to a single file or to a directory where qlog files will be created.</p>
   *
   * @param qLogConfig the qlog config
   * @return this exact object instance
   */
  public QuicEndpointOptions setQLogConfig(QLogConfig qLogConfig) {
    this.qLogConfig = qLogConfig;
    return this;
  }

  public JsonObject toJson() {
    throw new UnsupportedOperationException();
  }
}
