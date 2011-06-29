package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.net.Socket;

public class Client {
  public static void connect(int port, final Callback<Connection> connectCallback) {
    connect(port, "localhost", null, null, connectCallback);
  }

  public static void connect(int port, String host, final Callback<Connection> connectCallback) {
    connect(port, host, null, null, connectCallback);
  }

  public static void connect(int port, String host, final String username, final String password,
                             final Callback<Connection> connectCallback) {
    org.nodex.core.net.Client.connect(port, host, new Callback<Socket>() {
      public void onEvent(Socket sock) {
        final Connection conn = new Connection(sock);
        conn.connect(username, password, new NoArgCallback() {
          public void onEvent() {
            connectCallback.onEvent(conn);
          }
        });
      }
    });
  }
}


