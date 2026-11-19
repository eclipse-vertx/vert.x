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
import io.vertx.core.net.endpoint.ServerEndpoint;

import java.util.function.Predicate;

/**
 * Protocol filters, those can safely be used as endpoint view keys.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
enum ProtocolFilter implements Predicate<ServerEndpoint> {

  H3(HttpProtocol.H3),
  H2(HttpProtocol.H2),
  HTTP_1_1(HttpProtocol.HTTP_1_1),
  HTTP_1_0(HttpProtocol.HTTP_1_0);

  final HttpProtocol protocol;

  ProtocolFilter(HttpProtocol protocol) {
    this.protocol = protocol;
  }

  static ProtocolFilter of(HttpProtocol protocol) {
    for (ProtocolFilter filter : values()) {
      if (filter.protocol == protocol) {
        return filter;
      }
    }
    throw new AssertionError();
  }

  @Override
  public boolean test(ServerEndpoint serverEndpoint) {
    return protocol.id().equals(serverEndpoint.protocolId());
  }
}
