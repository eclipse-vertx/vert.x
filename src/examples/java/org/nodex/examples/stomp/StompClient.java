package org.nodex.examples.stomp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.stomp.Client;
import org.nodex.core.stomp.Connection;
import org.nodex.core.stomp.MessageCallback;

import java.util.Map;

/**
 * User: tfox
 * Date: 28/06/11
 * Time: 12:37
 */
public class StompClient {

  public static void main(String[] args) throws Exception {

    Client.connect(8080, new Callback<Connection>() {
      public void onEvent(final Connection conn) {

        // Subscribe to a topic
        conn.subscribe("test-topic", new MessageCallback() {
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
          conn.send("test-topic", Buffer.fromString("message " + i), true).onComplete(new NoArgCallback() {
            public void onEvent() {
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
