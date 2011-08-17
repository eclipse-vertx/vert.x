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

package org.nodex.examples.fanout;

import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.nodex.core.shared.SharedSet;

public class FanoutServer {
  public static void main(String[] args) throws Exception {

    final SharedSet<Long> connections = new SharedSet<>();

    NetServer server = new NetServer(new NetConnectHandler() {
      public void onConnect(final NetSocket socket) {
        connections.add(socket.writeActorID);
        socket.dataHandler(new DataHandler() {
          public void onData(Buffer buffer) {
            for (Long actorID: connections) {
              Nodex.instance.sendMessage(actorID, buffer);
            }
          }
        });
        socket.closedHandler(new Runnable() {
          public void run() {
            connections.remove(socket);
          }
        });
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.close();
  }
}
