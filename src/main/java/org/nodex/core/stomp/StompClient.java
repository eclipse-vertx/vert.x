package org.nodex.core.stomp;

import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetSocket;

public class StompClient {
  public static void connect(int port, final StompConnectHandler connectHandler) {
    connect(port, "localhost", null, null, connectHandler);
  }

  public static void connect(int port, String host, final StompConnectHandler connectHandler) {
    connect(port, host, null, null, connectHandler);
  }

  public static void connect(int port, String host, final String username, final String password,
                             final StompConnectHandler connectHandler) {
    NetClient.createClient().connect(port, host, new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        final StompConnection conn = new StompConnection(sock);
        conn.connect(username, password, new Runnable() {
          public void run() {
            connectHandler.onConnect(conn);
          }
        });
      }
    });
  }
}


