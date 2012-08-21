package org.vertx.java.core.sockjs;

import org.vertx.java.core.json.JsonObject;

/**
 * A listener for events on the EventBusBridge. Register an instance of this class on the SockJSServer to intercept
 * events on the EventBusBridge.
 */
public interface EventBusBridgeListener {

  /**
   * This method gets fired when a message gets sent to an address.
   * @param writeHandlerId The socket ID of the client who sent the message.
   * @param address The address this message was sent to.
   * @param message The sent message.
   * @return True, if the message should still be sent to the address, false otherwise.
   */
  boolean sendingMessage(String writeHandlerId, final String address, JsonObject message);

  /**
   * This method gets fired when a message gets published to an address.
   * @param writeHandlerId The socket ID of the client who sent the message.
   * @param address The address this message was sent to.
   * @param message The sent message.
   * @return True, if the message should still be published to the address, false otherwise.
   */
  boolean publishingMessage(String writeHandlerId, final String address, JsonObject message);

  /**
   * This method gets fired when a client registered to an address. It should return whether the handler should still
   * be registered.
   * @param writeHandlerId The socket ID of the client who registered on the address.
   * @param address The address the newly registered handler listens on.
   * @return True, if the handler should still be registered, false otherwise.
   */
  boolean registeringHandler(String writeHandlerId, final String address);

  /**
   * This method gets fired when a client unregistered a handler for an address.
   * @param writeHandlerId The socket ID of the client who registered on the address.
   * @param address The address the unregistered handler listened on.
   */
  void unregisteredHandler(String writeHandlerId, final String address);

  /**
   * This method gets fired when a client disconnects.
   * @param writeHandlerId The socket ID of the client who disconnected.
   */
  void clientDisconnected(String writeHandlerId);

}
