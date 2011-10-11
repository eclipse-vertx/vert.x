/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.examples.old.stomp;

import org.vertx.java.addons.old.stomp.StompClient;
import org.vertx.java.addons.old.stomp.StompConnectHandler;
import org.vertx.java.addons.old.stomp.StompConnection;
import org.vertx.java.addons.old.stomp.StompMsgCallback;
import org.vertx.java.core.buffer.Buffer;

import java.util.Map;

public class StompPerf {

  private static volatile long start = 0;

  public static void main(String[] args) throws Exception {

    StompClient.connect(8181, new StompConnectHandler() {
      public void onConnect(final StompConnection conn) {
        final int warmup = 500000;
        final int numMessages = 1000000;
        conn.subscribe("test-topic", new StompMsgCallback() {
          int count;

          public void onMessage(Map<String, String> headers, Buffer body) {
            count++;
            if (count == warmup + numMessages) {
              double rate = 1000 * (double) numMessages / (System.currentTimeMillis() - start);
              System.out.println("Runnable, rate " + rate);
            }
          }
        });
        Buffer buff = Buffer.create("msg");
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
