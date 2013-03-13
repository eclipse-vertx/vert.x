package eventbusbridge;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.EventBusBridgeHook;
import org.vertx.java.core.sockjs.SockJSSocket;

public class ServerHook implements EventBusBridgeHook {
  Logger logger;

  public ServerHook(Logger logger) {
    this.logger = logger;
  }

  /**
   * The socket has been closed
   *
   * @param sock The socket
   */
  public void handleSocketClosed(SockJSSocket sock) {
    logger.info("handleSocketClosed, sock = " + sock);
  }

  /**
   * Client is sending or publishing on the socket
   *
   * @param sock    The sock
   * @param send    if true it's a send else it's a publish
   * @param msg     The message
   * @param address The address the message is being sent/published to
   * @return true To allow the send/publish to occur, false otherwise
   */
  public boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address) {
    logger.info("handleSendOrPub, sock = " + sock + ", send = " + send + ", address = " + address);
    logger.info(msg);
    return true;
  }

  /**
   * Client is registering a handler
   *
   * @param sock    The socket
   * @param address The address
   * @return true to let the registration occur, false otherwise
   */
  public boolean handlePreRegister(SockJSSocket sock, String address) {
    logger.info("handlePreRegister, sock = " + sock + ", address = " + address);
    return true;
  }

  public void handlePostRegister(SockJSSocket sock, String address) {
    logger.info("handlePostRegister, sock = " + sock + ", address = " + address);
  }

  /**
   * Client is unregistering a handler
   *
   * @param sock    The socket
   * @param address The address
   */
  public boolean handleUnregister(SockJSSocket sock, String address) {
    logger.info("handleUnregister, sock = " + sock + ", address = " + address);
    return true;
  }

}