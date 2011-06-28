package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.net.Socket;

public class Client {
  public static void connect(int port, final Callback<Connection> connectCallback) {
    connect(port, "localhost", connectCallback);
  }

  public static void connect(int port, String host, final Callback<Connection> connectCallback) {
    org.nodex.core.net.Client.connect(port, host, new Callback<Socket>() {
      public void onEvent(Socket sock) {
        connectCallback.onEvent(new Connection(sock));
      }
    });
  }
}


