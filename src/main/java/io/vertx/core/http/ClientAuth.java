package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Configures the engine to require/request client authentication.
 * <p/>
 * Created by manishk on 10/2/2015.
 */
@VertxGen
public enum ClientAuth {

  /**
   * No client authentication is requested or required.
   */
  NONE,

  /**
   * Accept authentication if presented by client. If this option is set and the client chooses
   * not to provide authentication information about itself, the negotiations will continue.
   */
  REQUEST,

  /**
   * Require client to present authentication, if not presented then negotiations will be declined.
   */
  REQUIRED
}
