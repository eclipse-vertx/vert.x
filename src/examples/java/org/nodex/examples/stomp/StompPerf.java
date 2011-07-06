package org.nodex.examples.stomp;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.stomp.StompClient;
import org.nodex.core.stomp.StompConnection;
import org.nodex.core.stomp.StompMsgCallback;

import java.util.Map;

/**
 * User: tfox
 * Date: 28/06/11
 * Time: 12:37
 */
public class StompPerf {

  private static volatile long start = 0;

  public static void main(String[] args) throws Exception {

    StompClient.connect(8080, new Callback<StompConnection>() {
      public void onEvent(final StompConnection conn) {
        final int warmup = 500000;
        final int numMessages = 1000000;
        conn.subscribe("test-topic", new StompMsgCallback() {
          int count;

          public void onMessage(Map<String, String> headers, Buffer body) {
            count++;
            if (count == warmup + numMessages) {
              double rate = 1000 * (double) numMessages / (System.currentTimeMillis() - start);
              System.out.println("Done, rate " + rate);
            }
          }
        });
        Buffer buff = Buffer.fromString("msg");
        for (int i = 0; i < warmup; i++) {
          conn.send("test-topic", buff);
        }
        start = System.currentTimeMillis();
        for (int i = 0; i < numMessages; i++) {
          conn.send("test-topic", buff);
        }
      }
    });

    System.out.println("Any key to exit");
    System.in.read();
  }
}
