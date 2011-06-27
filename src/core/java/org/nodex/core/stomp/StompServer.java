package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.net.Server;
import org.nodex.core.net.Socket;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:33
 */
public class StompServer {
  public static Server createServer(final Callback<Connection> connectCallback) {
    return Server.createServer(new Callback<Socket>() {
      public void onEvent(Socket socket) {
        connectCallback.onEvent(new Connection(socket));
      }
    });
  }

}
