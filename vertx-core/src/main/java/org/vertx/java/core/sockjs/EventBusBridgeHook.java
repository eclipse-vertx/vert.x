package org.vertx.java.core.sockjs;

import org.vertx.java.core.json.JsonObject;

/**
 * A hook that you can use to receive various events on the EventBusBridge.<p>
 */
public interface EventBusBridgeHook {

  /**
   * The socket has been closed
   * @param sock The socket
   */
  void handleSocketClosed(SockJSSocket sock);

  /**
   * Client is sending or publishing on the socket
   * @param sock The sock
   * @param send if true it's a send else it's a publish
   * @param msg The message
   * @param address The address the message is being sent/published to
   * @return true To allow the send/publish to occur, false otherwise
   */
  boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address);

  /**
   * Called before client registers a handler
   * @param sock The socket
   * @param address The address
   * @return true to let the registration occur, false otherwise
   */
  boolean handlePreRegister(SockJSSocket sock, String address);

  /**
   * Called after client registers a handler
   * @param sock The socket
   * @param address The address
   */
  void handlePostRegister(SockJSSocket sock, String address);

  /**
   * Client is unregistering a handler
   * @param sock The socket
   * @param address The address
   */
  boolean handleUnregister(SockJSSocket sock, String address);
}
