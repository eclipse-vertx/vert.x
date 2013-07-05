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

package vertx.tests.core.net;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FanoutServer extends Verticle {

  protected TestUtils tu;

  private NetServer server;

  public void start(final Future<Void> startedResult) {
    tu = new TestUtils(vertx);

    final Set<String> connections = vertx.sharedData().getSet("conns");

    server = vertx.createNetServer();
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        tu.checkThread();
        connections.add(socket.writeHandlerID());
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkThread();
            for (String actorID : connections) {
              vertx.eventBus().publish(actorID, buffer);
            }
          }
        });
        socket.closeHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            connections.remove(socket.writeHandlerID());
          }
        });
      }
    });
    server.listen(1234, new AsyncResultHandler<NetServer>() {
      @Override
      public void handle(AsyncResult<NetServer> ar) {
        if (ar.succeeded()) {
          tu.appReady();
          startedResult.setResult(null);
        } else {
          ar.cause().printStackTrace();
        }
      }
    });
  }

  public void stop() {
    server.close(new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> res) {
        tu.checkThread();
        tu.appStopped();
      }
    });
  }

}
