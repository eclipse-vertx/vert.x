/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.examples.timeoutperf;

import org.vertx.java.core.Handler;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.deploy.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Server3 extends Verticle {

  volatile long last;

  @Override
  public void start() throws Exception {
    NetServer server = vertx.createNetServer();
    server.setAcceptBacklog(100000);
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        long now = System.currentTimeMillis();
        if (last != 0) {
          System.out.println("Connect gap: " + (now - last));
        }
        last = now;
      }
    });
    server.listen(8080);
  }
}
