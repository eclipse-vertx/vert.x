package pubsub;

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

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.platform.Verticle;

import java.util.Set;

public class PubSubServer extends Verticle {

  public void start() {
    vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
          public void handle(Buffer frame) {
            String line = frame.toString().trim();
            System.out.println("Line is " + line);
            String[] parts = line.split("\\,");
            if (line.startsWith("subscribe")) {
              System.out.println("Topic is " + parts[1]);
              Set<String> set = vertx.sharedData().getSet(parts[1]);
              set.add(socket.writeHandlerID);
            } else if (line.startsWith("unsubscribe")) {
              vertx.sharedData().getSet(parts[1]).remove(socket.writeHandlerID);
            } else if (line.startsWith("publish")) {
              System.out.println("Publish to topic is " + parts[1]);
              Set<String> actorIDs = vertx.sharedData().getSet(parts[1]);
              for (String actorID : actorIDs) {
                System.out.println("Sending to verticle");
                vertx.eventBus().publish(actorID, new Buffer(parts[2]));
              }
            }
          }
        }));
      }
    }).listen(1234);
  }
}
