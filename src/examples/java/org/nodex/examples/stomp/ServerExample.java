package org.nodex.examples.stomp;

import org.nodex.core.net.NetServer;
import org.nodex.core.stomp.StompServer;

public class ServerExample {
  public static void main(String[] args) throws Exception {
    NetServer server = StompServer.createServer().listen(8080);

    System.out.println("Any key to exit");
    System.in.read();
    server.stop();
  }
}
