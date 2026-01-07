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

import java.time.Duration;
import java.util.Objects;

/**
 * An alternative of an origin.
 */
class OriginAlternative {

  final HttpProtocol protocol;
  final HostAndPort authority;

  OriginAlternative(HttpProtocol protocol, HostAndPort authority) {
    this.protocol = protocol;
    this.authority = authority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(protocol, authority);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof OriginAlternative) {
      OriginAlternative that = (OriginAlternative) obj;
      return protocol == that.protocol && authority.equals(that.authority);
    }
    return false;
  }
}
