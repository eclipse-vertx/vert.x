package io.vertx.core.spi.naming;

import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;

/**
 * Name resolver SPI.
 */
public interface NameResolver<S> {

  /**
   * Resolve a name to the resolver state for this name
   * @param name the name to resolve
   * @return a future notified with the result
   */
  Future<S> resolve(String name);

  /**
   * Pick a socket address for the state.
   *
   * @param state the state
   * @return the resolved socket address
   */
  SocketAddress pickAddress(S state);

  /**
   * Remove a stale address from the state.
   *
   * @param state the state to update
   * @param address the stale address
   * @return {@code} true when the state should be disposed, i.e it contains no addresses
   */
  boolean removeAddress(S state, SocketAddress address);

  /**
   * Dispose the state.
   *
   * @param state the state
   */
  void dispose(S state);

}
