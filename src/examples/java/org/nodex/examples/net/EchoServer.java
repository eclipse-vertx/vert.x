package org.nodex.examples.net;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;

public class EchoServer {
  public static void main(String[] args) throws Exception {
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(final NetSocket socket) {
        System.out.println("Sever conttect");
        socket.data(new DataHandler() {
          public void onData(Buffer buffer) {
            socket.write(buffer);
          }
        });
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.close();
  }
}
