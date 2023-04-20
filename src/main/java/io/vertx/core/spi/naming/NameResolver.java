package io.vertx.core.spi.naming;

import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;

public interface NameResolver<S> {

  Future<S> resolve(String name);

  SocketAddress pickName(S state);

  boolean removeName(S state, SocketAddress socketAddress);

  void dispose(S state);

}
