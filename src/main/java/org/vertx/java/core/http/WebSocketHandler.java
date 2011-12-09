package org.vertx.java.core.http;

import org.vertx.java.core.Handler;

/**
 *
 * Handler for websockets.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface WebSocketHandler extends Handler<WebSocket> {

  /**
   * Before sending back a response for a WebSocket handshake the server will first call the {@link #accept} method
   * to see if the WebSocket should be accepted at that path.
   * @param path The path of the WebSocket upgrade request
   * @return true to accept the WebSocket, or false to reject it. Rejected upgrade requests will have a 404 served
   * to them
   */
  boolean accept(String path);

}
