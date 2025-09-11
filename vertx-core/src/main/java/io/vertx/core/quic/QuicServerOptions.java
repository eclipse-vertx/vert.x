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

import io.vertx.core.net.ServerSSLOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicServerOptions extends QuicEndpointOptions {

  public QuicServerOptions() {
  }

  public QuicServerOptions(QuicServerOptions other) {
    super(other);
  }

  @Override
  public ServerSSLOptions getSslOptions() {
    return (ServerSSLOptions) super.getSslOptions();
  }

  @Override
  protected ServerSSLOptions getOrCreateSSLOptions() {
    return new ServerSSLOptions();
  }
}
