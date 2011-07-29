package org.nodex.examples.stomp;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.stomp.StompConnectHandler;
import org.nodex.core.stomp.StompConnection;
import org.nodex.core.stomp.StompMsgCallback;

import java.util.Map;

/**
 * User: tfox
 * Date: 28/06/11
 * Time: 12:37
 */
public class ClientExample {

  public static void main(String[] args) throws Exception {

    org.nodex.core.stomp.StompClient.connect(8181, new StompConnectHandler() {
      public void onConnect(final StompConnection conn) {

        // Subscribe to a topic
        conn.subscribe("test-topic", new StompMsgCallback() {
          public void onMessage(Map<String, String> headers, Buffer body) {
            System.out.println("Received message: " + body.toString());
          }
        });

        // Send some messages (without receipt)
        for (int i = 0; i < 5; i++) {
          conn.send("test-topic", Buffer.fromString("message " + i));
        }

        // Now send some more with receipts
        for (int i = 5; i < 10; i++) {
          final int count = i;
          conn.send("test-topic", Buffer.fromString("message " + i), new Runnable() {
            public void run() {
              System.out.println("Got receipt " + count);
            }
          });
        }
      }
    });

    System.out.println("Any key to exit");
    System.in.read();
  }
}
