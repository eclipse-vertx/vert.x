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

package org.vertx.tests.core.net;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shared.SharedData;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedNetTest extends TestBase {

  private static final Logger log = Logger.getLogger(SharedNetTest.class);


  @Test
  public void testConnectionDistribution() throws Exception {
    final int dataLength = 10;
    final int connections = 50;
    final int serverLoops = 5;
    final int serversPerLoop = 3;
    final CountDownLatch serverCloseLatch = new CountDownLatch(serverLoops * serversPerLoop);
    final CountDownLatch listenLatch = new CountDownLatch(serverLoops * serversPerLoop);

    final Set<Long> serverHandlers = SharedData.getSet("servers");
    final Set<Integer> connectedServers = SharedData.getSet("connected");

    for (int i = 0; i < serverLoops; i++) {
      VertxInternal.instance.go(new Runnable() {
        public void run() {
          final ContextChecker checker = new ContextChecker();

          for (int j = 0; j < serversPerLoop; j++) {
            final NetServer server = new NetServer();

            final long actorID = Vertx.instance.registerHandler(new Handler<String>() {
              public void handle(String msg) {
                checker.check();
                server.close(new SimpleHandler() {
                  public void handle() {
                    checker.check();
                    serverCloseLatch.countDown();
                  }
                });
              }
            });

            serverHandlers.add(actorID);

            server.connectHandler(new Handler<NetSocket>() {
              public void handle(final NetSocket sock) {

                checker.check();

                sock.dataHandler(new Handler<Buffer>() {
                  public void handle(Buffer data) {
                    checker.check();
                    sock.write(data);    // Send it back to client
                  }
                });

                connectedServers.add(System.identityHashCode(server));

              }
            }).listen(8181);

            listenLatch.countDown();
          }
        }
      });
    }

    listenLatch.await(5, TimeUnit.SECONDS);

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        final NetClient client = new NetClient();

        final ContextChecker checker = new ContextChecker();

        final long actorID = Vertx.instance.registerHandler(new Handler<String>() {
          int count;
          public void handle(String msg) {
            checker.check();
            count++;
            if (count == connections) {
              //All client connections closed - now tell the servers to shutdown
              client.close();
              for (Long sactorID: serverHandlers) {
                Vertx.instance.sendToHandler(sactorID, "foo");
              }
            }
          }
        });

        for (int i = 0; i < connections; i++) {

          client.connect(8181, new Handler<NetSocket>() {
            public void handle(final NetSocket sock) {
              final Buffer buff = Buffer.create(0);
              sock.dataHandler(new Handler<Buffer>() {
                public void handle(Buffer data) {
                  checker.check();
                  buff.appendBuffer(data);
                  if (buff.length() == dataLength) {
                    sock.close();
                  }
                }
              });
              sock.closedHandler(new SimpleHandler() {
                public void handle() {
                  checker.check();
                  Vertx.instance.sendToHandler(actorID, "foo");
                }
              });
              Buffer sendBuff = Utils.generateRandomBuffer(dataLength);
              sock.write(sendBuff);
            }
          });
        }
      }
    });

    azzert(serverCloseLatch.await(5, TimeUnit.SECONDS));
    azzert(connectedServers.size() == serverLoops * serversPerLoop);

    SharedData.removeSet("servers");
    SharedData.removeSet("connected");

    throwAssertions();
  }

}
