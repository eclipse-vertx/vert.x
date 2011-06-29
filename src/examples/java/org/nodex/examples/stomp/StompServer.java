package org.nodex.examples.stomp;

import org.nodex.core.net.Server;

public class StompServer {
  public static void main(String[] args) throws Exception {
    Server server = org.nodex.core.stomp.StompServer.createServer().listen(8080);

    System.out.println("Any key to exit");
    System.in.read();
    server.stop();
  }
}
