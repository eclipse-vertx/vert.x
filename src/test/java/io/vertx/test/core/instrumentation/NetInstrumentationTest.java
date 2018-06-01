/*
 * Copyright 2015 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class NetInstrumentationTest extends InstrumentationTestBase {

  @Test
  public void testNetServer() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        assertSame(cont, instrumentation.current());
        so.handler(buff -> {
          assertSame(cont, instrumentation.current());
          so.write(buff);
          so.closeHandler(v -> {
            assertSame(cont, instrumentation.current());
            testComplete();
          });
        });
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    cont.suspend();
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", onSuccess(so -> {
      so.write("HELLO");
      assertNull(instrumentation.current());
      so.handler(buff -> {
        assertNull(instrumentation.current());
        so.close();
      });
    }));
    await();
  }

  @Test
  public void testNetServerDrainHandler() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> resume = new CompletableFuture<>();
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        while (!so.writeQueueFull()) {
          so.write(TestUtils.randomBuffer(1024));
        }
        so.drainHandler(v -> {
          so.close();
        });
        resume.complete(null);
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    cont.suspend();
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", onSuccess(so -> {
      so.pause();
      resume.thenAccept(v1 -> {
        so.resume();
        so.closeHandler(v2 -> {
          testComplete();
        });
      });
    }));
    await();
  }

  @Test
  public void testNetClient() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        so.write("ping");
        so.handler(v -> {
          so.close();
        });
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    client.connect(1234, "localhost", onSuccess(so -> {
      assertSame(cont, instrumentation.current());
      so.handler(buff -> {
        assertSame(cont, instrumentation.current());
        so.write("pong");
        so.closeHandler(v -> {
          assertSame(cont, instrumentation.current());
          testComplete();
        });
      });
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testNetClientDrainHandler() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> resume = new CompletableFuture<>();
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        so.pause();
        resume.thenAccept(v1 -> {
          so.resume();
          so.closeHandler(v2 -> {
            testComplete();
          });
        });
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    client.connect(1234, "localhost", onSuccess(so -> {
      assertSame(cont, instrumentation.current());
      while (!so.writeQueueFull()) {
        so.write(TestUtils.randomBuffer(1024));
      }
      so.drainHandler(v -> {
        assertSame(cont, instrumentation.current());
        so.close();
      });
      resume.complete(null);
    }));
    cont.suspend();
    await();
  }
}
