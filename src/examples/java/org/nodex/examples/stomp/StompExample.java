package org.nodex.examples.stomp;

import org.nodex.core.Callback;
import org.nodex.core.net.Server;
import org.nodex.core.stomp.Connection;
import org.nodex.core.stomp.Frame;
import org.nodex.core.stomp.StompServer;

public class StompExample {
  public static void main(String[] args) throws Exception {
    Server server = StompServer.createServer(new Callback<Connection>() {
      public void onEvent(final Connection connection) {
        connection.data(new Callback<Frame>() {
          public void onEvent(Frame frame) {
            System.out.println("Received STOMP frame: " + frame.command);
          }
        });
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.stop();
  }
}
