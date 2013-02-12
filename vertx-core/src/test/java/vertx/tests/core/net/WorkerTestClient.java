package vertx.tests.core.net;
/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.testframework.TestClientBase;

public class WorkerTestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  /*
  Make sure NetClient and NetServer can be used from worker verticles
   */
  public void testWorker() {

    final NetServer server = vertx.createNetServer();
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        Pump p = Pump.createPump(socket, socket);
        p.start();
      }
    });
    server.listen(1234);

    final NetClient client = vertx.createNetClient();
    client.connect(1234, new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            server.close(new SimpleHandler() {
              public void handle() {
                client.close();
                tu.testComplete();
              }
            });

          }
        });
        socket.write("foo");
      }
    });
  }
}
