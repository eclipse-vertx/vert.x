package io.vertx.core.http.impl;

import io.vertx.core.net.SocketAddress;

public class OriginServer {

  public final SocketAddress origin;

  public OriginServer(SocketAddress origin) {
    this.origin = origin;
  }
}
