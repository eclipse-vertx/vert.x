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

/**
 * Config operations of a Quic client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicClientOptions extends QuicEndpointOptions {

  public QuicClientOptions() {
  }

  public QuicClientOptions(QuicClientOptions other) {
    super(other);
  }

  @Override
  public QuicClientOptions setQLogConfig(QLogConfig qLogConfig) {
    return (QuicClientOptions) super.setQLogConfig(qLogConfig);
  }

  @Override
  public ClientSSLOptions getSslOptions() {
    return (ClientSSLOptions) super.getSslOptions();
  }

  @Override
  protected ClientSSLOptions getOrCreateSSLOptions() {
    return new ClientSSLOptions();
  }
}
