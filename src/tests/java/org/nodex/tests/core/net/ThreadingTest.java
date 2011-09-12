/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.core.net;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleEventHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.net.NetClient;
import org.nodex.java.core.net.NetServer;
import org.nodex.java.core.net.NetSocket;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadingTest extends TestBase {

  @Test
  // Test that all handlers for a connection are executed with same context
  public void testNetHandlers() throws Exception {
    final int dataLength = 10000;
    final CountDownLatch clientCloseLatch = new CountDownLatch(1);
    final CountDownLatch serverCloseLatch = new CountDownLatch(1);

    new NodexMain() {
      public void go() throws Exception {

        final NetServer server = new NetServer();

        final long actorId = Nodex.instance.registerHandler(new EventHandler<String>() {
          public void onEvent(String msg) {
            server.close(new SimpleEventHandler() {
              public void onEvent() {
                serverCloseLatch.countDown();
              }
            });
          }
        });

        server.connectHandler(new EventHandler<NetSocket>() {
          public void onEvent(final NetSocket sock) {
            final ContextChecker checker = new ContextChecker();
            sock.dataHandler(new EventHandler<Buffer>() {
              public void onEvent(Buffer data) {
                checker.check();
                sock.write(data);    // Send it back to client
              }
            });
            sock.closedHandler(new SimpleEventHandler() {
              public void onEvent() {
                checker.check();
                Nodex.instance.sendToHandler(actorId, "foo");
              }
            });
          }
        }).listen(8181);

        NetClient client = new NetClient();

        client.connect(8181, new EventHandler<NetSocket>() {
          public void onEvent(final NetSocket sock) {
            final ContextChecker checker = new ContextChecker();
            final Buffer buff = Buffer.create(0);
            sock.dataHandler(new EventHandler<Buffer>() {
              public void onEvent(Buffer data) {
                checker.check();
                buff.appendBuffer(data);
                if (buff.length() == dataLength) {
                  sock.close();
                }
              }
            });
            sock.closedHandler(new SimpleEventHandler() {
              public void onEvent() {
                checker.check();
                clientCloseLatch.countDown();
              }
            });
            Buffer sendBuff = Utils.generateRandomBuffer(dataLength);
            sock.write(sendBuff);
          }
        });
      }
    }.run();

    assert serverCloseLatch.await(5, TimeUnit.SECONDS);
    assert clientCloseLatch.await(5, TimeUnit.SECONDS);

    throwAssertions();
  }

  @Test
  /* Test that event loops are shared across available connections */
  public void testMultipleEventLoops() throws Exception {
    final int loops = NodexInternal.instance.getCoreThreadPoolSize();
    final int connections = 100;
    final Map<Thread, Object> threads = new ConcurrentHashMap<>();
    final CountDownLatch clientConnectLatch = new CountDownLatch(loops);
    final AtomicInteger serverConnectCount = new AtomicInteger(0);
    final CountDownLatch serverConnectLatch = new CountDownLatch(1);

    new NodexMain() {
      public void go() throws Exception {

        final NetServer server = new NetServer();

        final long actorId = Nodex.instance.registerHandler(new EventHandler<String>() {
          public void onEvent(String msg) {
            server.close(new SimpleEventHandler() {
              public void onEvent() {
                serverConnectLatch.countDown();
              }
            });
          }
        });

        server.connectHandler(new EventHandler<NetSocket>() {
          public void onEvent(NetSocket sock) {
            threads.put(Thread.currentThread(), "foo");
            if (serverConnectCount.incrementAndGet() == connections) {
              Nodex.instance.sendToHandler(actorId, "foo");
            }
          }
        }).listen(8181);


        NetClient client = new NetClient();

        for (int i = 0; i < connections; i++) {
          client.connect(8181, new EventHandler<NetSocket>() {
            public void onEvent(NetSocket sock) {
              clientConnectLatch.countDown();
              sock.close();
            }
          });
        }
      }
    }.run();

    assert serverConnectLatch.await(5, TimeUnit.SECONDS);
    assert clientConnectLatch.await(5, TimeUnit.SECONDS);
    assert loops == threads.size();

    throwAssertions();
  }
}
