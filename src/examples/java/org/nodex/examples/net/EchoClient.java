package org.nodex.examples.net;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.net.Client;
import org.nodex.core.net.Socket;

public class EchoClient {
  public static void main(String[] args) throws Exception {

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
  }
}
