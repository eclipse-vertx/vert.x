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

package org.nodex.core.stomp;

import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class StompServer {

  public static NetServer createServer() {

    return NetServer.createServer(new NetConnectHandler() {

      private ConcurrentMap<String, List<StompConnection>> subscriptions = new ConcurrentHashMap<String, List<StompConnection>>();

      private synchronized void subscribe(String dest, StompConnection conn) {
        List<StompConnection> conns = subscriptions.get(dest);
        if (conns == null) {
          conns = new CopyOnWriteArrayList<StompConnection>();
          subscriptions.put(dest, conns);
        }
        conns.add(conn);
      }

      private synchronized void unsubscribe(String dest, StompConnection conn) {
        List<StompConnection> conns = subscriptions.get(dest);
        if (conns == null) {
          conns.remove(conn);
          if (conns.isEmpty()) {
            subscriptions.remove(dest);
          }
        }
      }

      private void checkReceipt(Frame frame, StompConnection conn) {
        String receipt = frame.headers.get("receipt");
        if (receipt != null) {
          conn.write(Frame.receiptFrame(receipt));
        }
      }

      public void onConnect(final NetSocket sock) {
        final StompServerConnection conn = new StompServerConnection(sock);
        conn.frameHandler(new FrameHandler() {
          public void onFrame(Frame frame) {
            if ("CONNECT".equals(frame.command)) {
              conn.write(Frame.connectedFrame(UUID.randomUUID().toString()));
              return;
            }
            //The following can have optional receipt
            if ("SUBSCRIBE".equals(frame.command)) {
              String dest = frame.headers.get("destination");
              subscribe(dest, conn);
            } else if ("UNSUBSCRIBE".equals(frame.command)) {
              String dest = frame.headers.get("destination");
              unsubscribe(dest, conn);
            } else if ("SEND".equals(frame.command)) {
              String dest = frame.headers.get("destination");
              frame.command = "MESSAGE";
              List<StompConnection> conns = subscriptions.get(dest);
              if (conns != null) {
                for (StompConnection conn : conns) {
                  frame.headers.put("message-id", UUID.randomUUID().toString());
                  conn.write(frame);
                }
              }
            }
            checkReceipt(frame, conn);
          }
        });
      }
    });
  }
}
