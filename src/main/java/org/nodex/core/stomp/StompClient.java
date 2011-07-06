package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetSocket;

public class StompClient {
  public static void connect(int port, final Callback<StompConnection> connectCallback) {
    connect(port, "localhost", null, null, connectCallback);
  }

  public static void connect(int port, String host, final Callback<StompConnection> connectCallback) {
    connect(port, host, null, null, connectCallback);
  }

  public static void connect(int port, String host, final String username, final String password,
                             final Callback<StompConnection> connectCallback) {
    NetClient.connect(port, host, new Callback<NetSocket>() {
      public void onEvent(NetSocket sock) {
        final StompConnection conn = new StompConnection(sock);
        conn.connect(username, password, new NoArgCallback() {
          public void onEvent() {
            connectCallback.onEvent(conn);
          }
        });
      }
    });
  }
}


