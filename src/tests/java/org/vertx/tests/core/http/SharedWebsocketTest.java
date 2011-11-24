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

package org.vertx.tests.core.http;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.Websocket;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shared.SharedData;
import org.vertx.tests.core.TestBase;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedWebsocketTest extends TestBase {

  private static final Logger log = Logger.getLogger(SharedWebsocketTest.class);

  @Test
  public void testHandlerDistribution() throws Exception {
    final String host = "localhost";
    final int port = 8181;
    final int numRequests = 50;
    final int serversPerLoop = 2;
    final int numServerLoops = 5;
    final Set<Integer> connectedServers = SharedData.getSet("servers");
    final CountDownLatch serversListening = new CountDownLatch(serversPerLoop * numServerLoops);
    final CountDownLatch serverCloseLatch = new CountDownLatch(serversPerLoop * numServerLoops);
    final Set<Long> servers = SharedData.getSet("srvrs");

    for (int i = 0; i < numServerLoops; i++) {
      VertxInternal.instance.go(new Runnable() {

        public void run() {

          for (int j = 0; j < serversPerLoop; j++) {
            final HttpServer server = new HttpServer();
            server.websocketHandler(new Handler<Websocket>() {

              public void handle(final Websocket ws) {
                connectedServers.add(System.identityHashCode(server));
              }
            }).listen(port, host);

            long actorID = Vertx.instance.registerHandler(new Handler<String>() {
              public void handle(String s) {
                server.close(new SimpleHandler() {
                  public void handle() {
                    serverCloseLatch.countDown();
                  }
                });
              }
            });

            servers.add(actorID);
            serversListening.countDown();
          }
        }
      });
    }

    azzert(serversListening.await(5, TimeUnit.SECONDS));

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final long actorID = Vertx.instance.registerHandler(new Handler<String>() {
          int count;
          public void handle(String s) {
            count++;
            if (count == numRequests) {
              //Close all the servers
              for (long id: servers) {
                Vertx.instance.sendToHandler(id, "bar");
              }
            }
          }
        });

        for (int i = 0; i < numRequests; i++) {
          final HttpClient client = new HttpClient().setPort(port).setHost(host);
          client.connectWebsocket("someuri", new Handler<Websocket>() {
            public void handle(Websocket ws) {
              Vertx.instance.sendToHandler(actorID, "bar");
              client.close();
            }
          });
        }
      }
    });

    azzert(serverCloseLatch.await(5, TimeUnit.SECONDS));
    azzert(connectedServers.size() == serversPerLoop * numServerLoops);

    SharedData.removeSet("servers");
    SharedData.removeSet("srvrs");

    throwAssertions();
  }
}
