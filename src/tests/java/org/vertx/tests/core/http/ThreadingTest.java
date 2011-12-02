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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
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
public class ThreadingTest extends TestBase {

  private static final Logger log = Logger.getLogger(ThreadingTest.class);


  @Test
  public void testHTTPHandlers() throws Exception {
    final String host = "localhost";
    final int port = 8181;
    final int numRequests = 50;
    final int serversPerLoop = 3;
    final int numServerLoops = 5;
    final CountDownLatch serverCloseLatch = new CountDownLatch(numServerLoops * serversPerLoop);
    final Set<Integer> connectedServers = SharedData.getSet("servers");
    final CountDownLatch serversListening = new CountDownLatch(serversPerLoop * numServerLoops);
    final Set<Long> servers = SharedData.getSet("serverHandlers");

    for (int i = 0; i < numServerLoops; i++) {
      VertxInternal.instance.go(new Runnable() {
        public void run() {
          final ContextChecker checker = new ContextChecker();
          for (int j = 0; j < serversPerLoop; j++) {
            final HttpServer server = new HttpServer();
            long actorID = Vertx.instance.registerHandler(new Handler<String>() {
              public void handle(String s) {
                checker.check();
                server.close(new SimpleHandler() {
                  public void handle() {
                    log.info("closed server");
                    serverCloseLatch.countDown();
                  }
                });
              }
            });
            servers.add(actorID);
            server.requestHandler(new Handler<HttpServerRequest>() {
              public void handle(final HttpServerRequest req) {
                checker.check();
                connectedServers.add(System.identityHashCode(server));
                req.dataHandler(new Handler<Buffer>() {
                  public void handle(Buffer data) {
                    checker.check();
                  }
                });
                req.endHandler(new SimpleHandler() {
                  public void handle() {
                    checker.check();
                    req.response.end("bar");
                  }
                });
              }
            }).listen(port, host);
            serversListening.countDown();
          }

        }
      });
    }

    azzert(serversListening.await(5, TimeUnit.SECONDS));

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final long actorID = Vertx.instance.registerHandler(new Handler<String>() {
          int count = 0;
          public void handle(String s) {
            count++;
            if (count == numRequests) {
              for (long id: servers) {
                log.info("Sending msg to server");
                Vertx.instance.sendToHandler(id, "foo");
              }
            }
          }
        });


        final ContextChecker checker = new ContextChecker();
        for (int i = 0; i < numRequests; i++) {
          final HttpClient client = new HttpClient().setPort(port).setHost(host);
          HttpClientRequest request = client.put("someurl", new Handler<HttpClientResponse>() {
            public void handle(HttpClientResponse resp) {
              checker.check();
              resp.dataHandler(new Handler<Buffer>() {
                public void handle(Buffer data) {
                  checker.check();
                }
              });
              resp.endHandler(new SimpleHandler() {
                public void handle() {
                  checker.check();
                  client.close();
                  //requestLatch.countDown();
                  Vertx.instance.sendToHandler(actorID, "bar");
                }
              });
            }
          });
          request.end("foo");
        }
      }
    });

    azzert(serverCloseLatch.await(5, TimeUnit.SECONDS));
    log.info("connected servers " + connectedServers.size());
    azzert(connectedServers.size() == serversPerLoop * numServerLoops);
    SharedData.removeSet("servers");
    SharedData.removeSet("serverHandlers");

    throwAssertions();
  }

}
