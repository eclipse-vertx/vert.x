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
package io.vertx.core.http.impl;

import io.vertx.core.http.HttpProtocol;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class OriginServer {

  public final HttpProtocol protocol; // null <=> unknown
  public final HostAndPort authority; // the alt-authority field
  public final SocketAddress address; // the server socket address

  public OriginServer(HttpProtocol protocol, HostAndPort authority, SocketAddress address) {
    this.protocol = protocol;
    this.authority = authority;
    this.address = address;
  }

  @Override
  public String toString() {
    return "OriginServer[protocol=" + protocol + ",authority=" + authority + ",address=" + address + "]";
  }
}
