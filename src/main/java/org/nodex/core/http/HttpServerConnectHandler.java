package org.nodex.core.http;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:25
 */
public interface HttpServerConnectHandler {
  void onConnect(HttpServerConnection connection);
}
