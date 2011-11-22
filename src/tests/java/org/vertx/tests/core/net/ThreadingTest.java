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
import org.vertx.java.core.VertxMain;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ThreadingTest extends TestBase {

  private static final Logger log = Logger.getLogger(ThreadingTest.class);

  @Test
  // Test that all handlers for a connection are executed with same context
  public void testNetHandlers() throws Exception {
    final int dataLength = 10000;
    final int connections = 10;
    final CountDownLatch clientCloseLatch = new CountDownLatch(connections);
    final CountDownLatch serverCloseLatch = new CountDownLatch(connections);

    new VertxMain() {
      public void go() throws Exception {

        final NetServer server = new NetServer();

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {
            server.close(new SimpleHandler() {
              public void handle() {
                serverCloseLatch.countDown();
              }
            });
          }
        });

        final ContextChecker checker = new ContextChecker();

        server.connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket sock) {

            checker.check();

            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                checker.check();
                sock.write(data);    // Send it back to client
              }
            });
            sock.closedHandler(new SimpleHandler() {
              public void handle() {
                checker.check();
                Vertx.instance.sendToHandler(actorId, "foo");
              }
            });
          }
        }).listen(8181);

        NetClient client = new NetClient();


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
                  clientCloseLatch.countDown();
                }
              });
              Buffer sendBuff = Utils.generateRandomBuffer(dataLength);
              sock.write(sendBuff);
            }
          });
        }
      }
    }.run();

    assert serverCloseLatch.await(5, TimeUnit.SECONDS);
    assert clientCloseLatch.await(5, TimeUnit.SECONDS);

    throwAssertions();
  }

}
