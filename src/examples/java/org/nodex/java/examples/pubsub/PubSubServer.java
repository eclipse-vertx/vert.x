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

package org.nodex.java.examples.pubsub;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.net.NetServer;
import org.nodex.java.core.net.NetSocket;
import org.nodex.java.core.parsetools.RecordParser;
import org.nodex.java.core.shared.SharedData;

import java.util.Set;

public class PubSubServer extends NodexMain {

  public static void main(String[] args) throws Exception {
    new PubSubServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new NetServer().connectHandler(new EventHandler<NetSocket>() {
      public void onEvent(final NetSocket socket) {
        socket.dataHandler(RecordParser.newDelimited("\n", new EventHandler<Buffer>() {
          public void onEvent(Buffer frame) {
            String line = frame.toString().trim();
            String[] parts = line.split("\\,");
            if (line.startsWith("subscribe")) {
              Set<Long> set = SharedData.<Long>getSet(parts[1]);
              set.add(socket.writeHandlerID);
            } else if (line.startsWith("unsubscribe")) {
              SharedData.<Long>getSet(parts[1]).remove(socket.writeHandlerID);
            } else if (line.startsWith("publish")) {
              Set<Long> actorIDs = SharedData.getSet(parts[1]);
              for (Long actorID : actorIDs) {
                Nodex.instance.sendToHandler(actorID, Buffer.create(parts[2]));
              }
            }
          }
        }));
      }
    }).listen(8080);
  }
}
