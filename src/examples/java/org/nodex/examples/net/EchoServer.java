package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;

public class EchoServer {
  public static void main(String[] args) throws Exception {
    NetServer server = NetServer.createServer(new Callback<NetSocket>() {
      public void onEvent(final NetSocket socket) {
        socket.data(new Callback<Buffer>() {
          public void onEvent(Buffer buffer) {
            socket.write(buffer);
          }
        });
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.stop();
  }
}
