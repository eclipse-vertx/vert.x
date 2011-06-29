package org.nodex.examples.stomp;

import org.nodex.core.net.Server;
import org.nodex.core.stomp.StompServer;

public class ServerMain {
  public static void main(String[] args) throws Exception {
    Server server = StompServer.createServer().listen(8080);

    System.out.println("Any key to exit");
    System.in.read();
    server.stop();
  }
}
