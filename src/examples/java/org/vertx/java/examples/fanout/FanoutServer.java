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

package org.vertx.java.examples.fanout;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;

import java.util.Set;

public class FanoutServer implements VertxApp {

  private NetServer server;

  public void start()  {
    final Set<String> connections = SharedData.getSet("conns");

    System.out.println("connections is " + System.identityHashCode(connections));

    server = new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        connections.add(socket.writeHandlerID);
        System.out.println("Got a connection on app " + System.identityHashCode(FanoutServer.this));
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            System.out.println("Fanning out to " + connections.size() + " connections");
            for (String actorID : connections) {
              EventBus.instance.send(actorID, buffer);
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

  public void stop() {
    server.close();
  }
}
