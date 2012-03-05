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

package org.vertx.java.examples.echo;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Verticle;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;

public class EchoServer implements Verticle {

  private NetServer server;

  public void start() {
    server = new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        new Pump(socket, socket).start();
      }
    }).listen(1234);
  }

  public void stop() {
    server.close();
  }
}
