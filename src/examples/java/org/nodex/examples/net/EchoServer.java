package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.Server;
import org.nodex.core.net.Socket;

public class EchoServer {
  public static void main(String[] args) throws Exception {
    Server.createServer(new Callback<Socket>() {
      public void onEvent(final Socket socket) {
        socket.data(new Callback<Buffer>() {
          public void onEvent(Buffer buffer) {
            socket.write(buffer);
          }
        });
      }
    }).listen(8080, "localhost");

    System.out.println("Any key to exit");
    System.in.read();
  }
}
