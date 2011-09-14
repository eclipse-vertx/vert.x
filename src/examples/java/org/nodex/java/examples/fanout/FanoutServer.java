/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.examples.fanout;

import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleHandler;
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

    new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        connections.add(socket.writeHandlerID);
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            for (Long actorID : connections) {
              Nodex.instance.sendToHandler(actorID, buffer);
            }
          }
        });
        socket.closedHandler(new SimpleHandler() {
          public void handle() {
            connections.remove(socket.writeHandlerID);
          }
        });
      }
    }).listen(8080);
  }
}
