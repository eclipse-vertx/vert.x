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

package org.vertx.java.examples.pubsub;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxMain;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.core.shared.SharedData;

import java.util.Set;

public class PubSubServer extends VertxMain {

  public static void main(String[] args) throws Exception {
    new PubSubServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
          public void handle(Buffer frame) {
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
                Vertx.instance.sendToHandler(actorID, Buffer.create(parts[2]));
              }
            }
          }
        }));
      }
    }).listen(8080);
  }
}
