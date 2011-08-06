/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tests.core.net;

import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ThreadingTest extends TestBase {

  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  // Test that all handlers for a connection are executed with same context
  public void testNetHandlers() throws Exception {
    final int dataLength = 10000;
    final CountDownLatch serverClosedLatch = new CountDownLatch(1);
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final ContextChecker checker = new ContextChecker();
        sock.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            checker.check();
            sock.write(data);    // Send it back to client
          }
        });
        sock.closedHandler(new Runnable() {
          public void run() {
            checker.check();
            serverClosedLatch.countDown();
          }
        });
      }
    }).listen(8181);

    NetClient client = NetClient.createClient();

    final CountDownLatch clientClosedLatch = new CountDownLatch(1);
    client.connect(8181, new NetConnectHandler() {
      public void onConnect(final NetSocket sock) {
        final ContextChecker checker = new ContextChecker();
        final Buffer buff = Buffer.newDynamic(0);
        sock.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            checker.check();
            buff.append(data);
            if (buff.length() == dataLength) {
              sock.close();
            }
          }
        });
        sock.closedHandler(new Runnable() {
          public void run() {
            checker.check();
            clientClosedLatch.countDown();
          }
        });
        Buffer sendBuff = Utils.generateRandomBuffer(dataLength);
        sock.write(sendBuff);
        sock.close();
      }
    });

    assert serverClosedLatch.await(5, TimeUnit.SECONDS);
    assert clientClosedLatch.await(5, TimeUnit.SECONDS);

    awaitClose(server);
  }

  @Test
  /* Test that event loops are shared across available connections */
  public void testMultipleEventLoops() throws Exception {
    int loops = Nodex.instance.getCoreThreadPoolSize();
    int connections = 100;
    final Map<Thread, Object> threads = new ConcurrentHashMap<Thread, Object>();
    final CountDownLatch serverConnectLatch = new CountDownLatch(connections);
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        threads.put(Thread.currentThread(), "foo");
        serverConnectLatch.countDown();
      }
    }).listen(8181);

    final CountDownLatch clientConnectLatch = new CountDownLatch(loops);
    NetClient client = NetClient.createClient();

    for (int i = 0; i < connections; i++) {
      client.connect(8181, new NetConnectHandler() {
        public void onConnect(NetSocket sock) {
          clientConnectLatch.countDown();
          sock.close();
        }
      });
    }

    assert serverConnectLatch.await(5, TimeUnit.SECONDS);
    assert clientConnectLatch.await(5, TimeUnit.SECONDS);
    assert loops == threads.size();

    awaitClose(server);
  }
}
