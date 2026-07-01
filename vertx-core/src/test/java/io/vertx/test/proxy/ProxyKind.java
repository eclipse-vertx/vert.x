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
package io.vertx.test.proxy;

public enum ProxyKind {

  /**
   * HTTP CONNECT ssl proxy
   */
  HTTP,

  /**
   * HTTP proxy whose own connection (leg 1) is established over TLS
   */
  HTTPS,

  /**
   * SOCKS4/4a tcp proxy
   */
  SOCKS4,

  /**
   * SOCSK5 tcp proxy
   */
  SOCKS5,

  HA

}
