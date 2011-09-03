/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.examples.old.stomp;

import org.nodex.core.buffer.Buffer;
import org.nodex.mods.stomp.StompConnectHandler;
import org.nodex.mods.stomp.StompConnection;
import org.nodex.mods.stomp.StompMsgCallback;

import java.util.Map;

public class ClientExample {

  public static void main(String[] args) throws Exception {

    org.nodex.mods.stomp.StompClient.connect(8181, new StompConnectHandler() {
      public void onConnect(final StompConnection conn) {

        // Subscribe to a topic
        conn.subscribe("test-topic", new StompMsgCallback() {
          public void onMessage(Map<String, String> headers, Buffer body) {
            System.out.println("Received message: " + body.toString());
          }
        });

        // Send some messages (without receipt)
        for (int i = 0; i < 5; i++) {
          conn.send("test-topic", Buffer.create("message " + i));
        }

        // Now send some more with receipts
        for (int i = 5; i < 10; i++) {
          final int count = i;
          conn.send("test-topic", Buffer.create("message " + i), new Runnable() {
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
