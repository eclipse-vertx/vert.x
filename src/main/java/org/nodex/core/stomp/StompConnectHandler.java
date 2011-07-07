package org.nodex.core.stomp;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:52
 */
public abstract class StompConnectHandler {
  public abstract void onConnect(StompConnection connection);
}
