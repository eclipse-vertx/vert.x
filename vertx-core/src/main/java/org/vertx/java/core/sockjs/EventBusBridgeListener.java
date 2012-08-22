package org.vertx.java.core.sockjs;

import org.vertx.java.core.json.JsonObject;

/**
 * A listener for events on the EventBusBridge. Register an instance of this class on the SockJSServer to intercept
 * events on the EventBusBridge.
 */
public interface EventBusBridgeListener {

  /**
   * This method gets fired when a client disconnects.
   * @param writeHandlerId The socket ID of the client who disconnected.
   */
  void clientDisconnected(String writeHandlerId);

}
