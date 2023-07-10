package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.impl.HostAndPortImpl;

/**
 * A combination of host and port.
 */
@VertxGen
public interface HostAndPort {

  /**
   * Create an instance.
   *
   * @param host the host value
   * @param port the port value
   * @return the instance.
   */
  static HostAndPort create(String host, int port) {
    return new HostAndPortImpl(host, port);
  }

  /**
   * @return the host value
   */
  String host();

  /**
   * @return the port value
   */
  int port();

}
