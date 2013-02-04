package fanout;

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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;

import java.util.Set;

public class FanoutServer extends Verticle {

  public void start()  {
    final Set<String> connections = vertx.sharedData().getSet("conns");

    vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        connections.add(socket.writeHandlerID);
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            for (String actorID : connections) {
              vertx.eventBus().publish(actorID, buffer);
            }
          }
        });
        socket.closedHandler(new SimpleHandler() {
          public void handle() {
            connections.remove(socket.writeHandlerID);
          }
        });
      }
    }).listen(1234);
  }
}
