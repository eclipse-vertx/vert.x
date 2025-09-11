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
package io.vertx.core.quic;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetworkOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.ServerSSLOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class QuicEndpointOptions extends NetworkOptions {

  private QuicOptions transportOptions;
  private SSLOptions sslOptions;

  public QuicEndpointOptions() {
    this.transportOptions = new QuicOptions();
  }

  public QuicEndpointOptions(QuicEndpointOptions other) {
    super(other);

    this.transportOptions = other.transportOptions.copy();
    this.sslOptions = other.sslOptions.copy();
  }

  public QuicEndpointOptions(JsonObject json) {
    super(json);
  }

  public QuicOptions getTransportOptions() {
    return transportOptions;
  }

  public SSLOptions getSslOptions() {
    SSLOptions opts = sslOptions;
    if (opts == null) {
      opts = getOrCreateSSLOptions();
      sslOptions = opts;
    }
    return opts;
  }

  protected abstract SSLOptions getOrCreateSSLOptions();

  @Override
  public JsonObject toJson() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isReusePort() {
    throw new UnsupportedOperationException();
  }

  @Override
  public NetworkOptions setReusePort(boolean reusePort) {
    throw new UnsupportedOperationException();
  }
}
