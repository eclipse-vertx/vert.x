package org.nodex.examples.stomp;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.stomp.Client;
import org.nodex.core.stomp.Connection;
import org.nodex.core.stomp.Frame;

/**
 * User: tfox
 * Date: 28/06/11
 * Time: 12:37
 */
public class StompPerf {

  public static void main(String[] args) throws Exception {

    Client.connect(8080, new Callback<Connection>() {
      public void onEvent(final Connection conn) {
        try {
          final int warmup = 100000;
          final int numMessages = 200000;

          conn.subscribe("test-topic", new Callback<Frame>() {
            int count;
            long start;

            public void onEvent(Frame frame) {
              count++;

              if (count == warmup) {
                start = System.currentTimeMillis();
              }

              if (count == warmup + numMessages) {
                double rate = 1000 * (double) numMessages / (System.currentTimeMillis() - start);
                System.out.println("Done, rate " + rate);
              }
            }
          });
          for (int i = 0; i < numMessages + warmup; i++) {
            conn.send("test-topic", Buffer.fromString("message " + i));
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
