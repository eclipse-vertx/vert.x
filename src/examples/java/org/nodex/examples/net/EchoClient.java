package org.nodex.examples.net;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetSocket;

public class EchoClient {
  public static void main(String[] args) throws Exception {

    System.out.println("Startinh");

    NetClient.createClient().connect(8080, "localhost", new NetConnectHandler() {
      public void onConnect(NetSocket socket) {

        System.out.println("Connecting");

        socket.data(new DataHandler() {
          public void onData(Buffer buffer) {
            System.out.println("Net client receiving: " + buffer.toString("UTF-8"));
          }
        });

        System.out.println("Sending");

        //Now send some data
        for (int i = 0; i < 10; i++) {
          String str = "hello" + i + "\n";
          System.out.print("Net client sending: " + str);
          socket.write(Buffer.fromString(str));
        }
      }
    });

    System.out.println("Any key to exit");
    System.in.read();
  }

}
