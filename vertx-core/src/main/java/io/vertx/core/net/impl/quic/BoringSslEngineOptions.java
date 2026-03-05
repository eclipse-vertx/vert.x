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
package io.vertx.core.net.impl.quic;

import io.netty.handler.codec.quic.BoringSSLKeylog;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.spi.tls.QuicSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class BoringSslEngineOptions extends SSLEngineOptions {

  private final BoringSSLKeylog keylog;

  BoringSslEngineOptions(BoringSSLKeylog keylog) {
    this.keylog = keylog;
  }

  @Override
  public SSLEngineOptions copy() {
    return this;
  }

  @Override
  public SslContextFactory sslContextFactory() {
    return new QuicSslContextFactory(keylog);
  }
}
