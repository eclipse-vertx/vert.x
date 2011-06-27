package org.nodex.core.stomp;


import org.nodex.core.Callback;
import org.nodex.core.net.Socket;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:33
 */
public class StompServer {
  public static org.nodex.core.net.Server createServer(final Callback<Connection> connectCallback) {
    return org.nodex.core.net.Server.createServer(new Callback<Socket>() {
      public void onEvent(Socket socket) {
        Connection conn = new Connection(socket);
        connectCallback.onEvent(conn);
      }
    });
  }

}
