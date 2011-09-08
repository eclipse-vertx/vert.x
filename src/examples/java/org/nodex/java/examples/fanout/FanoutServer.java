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

package org.nodex.java.examples.fanout;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleEventHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.net.NetServer;
import org.nodex.java.core.net.NetSocket;
import org.nodex.java.core.shared.SharedData;

import java.util.Set;

public class FanoutServer extends NodexMain {
  public static void main(String[] args) throws Exception {
    new FanoutServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    final Set<Long> connections = SharedData.getSet("conns");

    new NetServer().connectHandler(new EventHandler<NetSocket>() {
      public void onEvent(final NetSocket socket) {
        connections.add(socket.writeHandlerID);
        socket.dataHandler(new EventHandler<Buffer>() {
          public void onEvent(Buffer buffer) {
            for (Long actorID : connections) {
              Nodex.instance.sendToHandler(actorID, buffer);
            }
          }
        });
        socket.closedHandler(new SimpleEventHandler() {
          public void onEvent() {
            connections.remove(socket.writeHandlerID);
          }
        });
      }
    }).listen(8080);
  }
}
