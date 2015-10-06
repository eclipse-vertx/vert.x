package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Configures the engine to require/request client authentication. Following are the options :
 * <p>
 * NONE - No client authentication is requested or required.
 * <p>
 * REQUEST - Accept authentication if presented by client. If this option is set and the client chooses
 *      not to provide authentication information about itself, the negotiations will continue.
 * <p>
 * REQUIRED - Require client to present authentication, if not presented then negotiations will be declined.
 * <p>
 * Created by manishk on 10/2/2015.
 */
@VertxGen
public enum ClientAuth {
    NONE, REQUEST, REQUIRED
}
