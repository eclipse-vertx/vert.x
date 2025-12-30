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
package io.vertx.core.http.impl;

import io.vertx.core.http.HttpProtocol;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;

/**
 * An alternative of an origin.
 */
class OriginAlternative {

  final HttpProtocol protocol;
  final HostAndPort authority;
  final SocketAddress socketAddress;
  final long expirationTimestamp;

  OriginAlternative(HttpProtocol protocol, HostAndPort authority, SocketAddress socketAddress, long expirationTimestamp) {
    this.protocol = protocol;
    this.authority = authority;
    this.socketAddress = socketAddress;
    this.expirationTimestamp = expirationTimestamp;
  }
}
