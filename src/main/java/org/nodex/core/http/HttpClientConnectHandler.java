package org.nodex.core.http;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 11:48
 */
public interface HttpClientConnectHandler {
  void onConnect(HttpClientConnection connection);
}
