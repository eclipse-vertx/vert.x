package org.nodex.core.http;

/**
 * User: timfox
 * Date: 26/07/2011
 * Time: 10:40
 */
public abstract class WebsocketConnectHandler {
  public abstract boolean onConnect(Websocket ws);
}
