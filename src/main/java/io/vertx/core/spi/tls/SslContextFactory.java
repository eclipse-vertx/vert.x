/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.tls;

import io.netty.handler.ssl.SslContext;
import io.vertx.core.impl.VertxInternal;

/**
 * A factory for Netty {@link SslContext}.
 */
public interface SslContextFactory {

  /**
   * Create and configure a Netty {@link SslContext}.
   *
   * @param vertx the vertx instance
   * @param serverName the server name
   * @param useAlpn whether to use ALPN
   * @param client {@literal true} client or server
   * @param trustAll whether to trust all servers (only for client)
   * @return the created {@link SslContext}
   */
  SslContext createContext(VertxInternal vertx,
                           String serverName,
                           boolean useAlpn,
                           boolean client,
                           boolean trustAll);

}
