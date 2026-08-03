/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;

/**
 * The type of a TCP proxy server.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
@VertxGen
public enum ProxyType {
  /**
   * HTTP CONNECT ssl proxy
   */
  HTTP,
  /**
   * HTTP proxy reached over SSL/TLS, i.e. the client opens an {@code https} connection to the proxy
   * itself before issuing {@code CONNECT} or absolute-URI requests. The proxying semantics are
   * identical to {@link #HTTP}; only the connection to the proxy is encrypted, configured via
   * {@link ProxyOptions#setSslOptions(ClientSSLOptions)}.
   * <p>
   * Note this is distinct from the {@code https_proxy} environment-variable convention, which
   * denotes the proxy used <em>for</em> {@code https} traffic rather than a proxy reached over SSL/TLS.
   */
  HTTPS,
  /**
   * SOCKS4/4a tcp proxy
   */
  SOCKS4,
  /**
   * SOCSK5 tcp proxy
   */
  SOCKS5
}
