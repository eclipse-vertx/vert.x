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
import org.vertx.java.core.Verticle;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.core.shareddata.SharedData;

import java.util.Set;

public class PubSubServer implements Verticle {

  private NetServer server;

  public void start() {
    server = new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
          public void handle(Buffer frame) {
            String line = frame.toString().trim();
            String[] parts = line.split("\\,");
            if (line.startsWith("subscribe")) {
              Set<String> set = SharedData.instance.getSet(parts[1]);
              set.add(socket.writeHandlerID);
            } else if (line.startsWith("unsubscribe")) {
              SharedData.instance.getSet(parts[1]).remove(socket.writeHandlerID);
            } else if (line.startsWith("publish")) {
              Set<String> actorIDs = SharedData.instance.getSet(parts[1]);
              for (String actorID : actorIDs) {
                EventBus.instance.send(actorID, Buffer.create(parts[2]));
              }
            }
          }
        }));
      }
    }).listen(1234);
  }

  public void stop() {
    server.close();
  }
}
