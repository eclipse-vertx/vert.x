package org.nodex.core.stomp;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:52
 */
public interface StompConnectHandler {
  void onConnect(StompConnection connection);
}
