package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.Client;
import org.nodex.core.net.Server;
import org.nodex.core.net.Socket;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 09:19
 * To change this template use File | Settings | File Templates.
 */
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

    System.out.println("Now creating client");
    Client.connect(8080, "localhost", new Callback<Socket>() {
      public void onEvent(Socket socket) {
        socket.data(new Callback<Buffer>() {
          public void onEvent(Buffer buffer) {
            System.out.println("Client receiving: " + buffer.toString("UTF-8"));
          }
        });

        //Now send some data
        for (int i = 0; i < 10; i++) {
          String str = "hello" + i + "\n";
          System.out.print("Client sending: " + str);
          socket.write(Buffer.fromString(str));
        }
      }
    });
    System.out.println("Any key to exit");
    System.in.read();
  }
}
