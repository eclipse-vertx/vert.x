package org.nodex.examples.stomp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.stomp.Client;
import org.nodex.core.stomp.Connection;
import org.nodex.core.stomp.Frame;

import java.util.HashMap;
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
          try
          {
            conn.subscribe("test-topic", new Callback<Frame>() {
              public void onEvent(Frame frame) {
                System.out.println("Received message: " + frame.body.toString());
              }
            });
            for (int i = 0; i < 5; i++) {
              conn.send("test-topic", Buffer.fromString("message " + i));
            }

            //Now send some with receipt
            for (int i = 5; i < 10; i++) {
              final int count = i;
              conn.send("test-topic", Buffer.fromString("message " + i), new NoArgCallback() {
                public void onEvent() {
                  System.out.println("Got receipt " + count);
                }
              });
            }

          } catch (Exception e) {
            e.printStackTrace();
          }
        }
    });

    System.out.println("Any key to exit");
    System.in.read();
  }
}
