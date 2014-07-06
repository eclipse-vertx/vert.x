/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.Registration;
import io.vertx.core.shareddata.Shareable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBusTest extends EventBusTestBase {

  private Vertx[] vertices;
  private static final String ADDRESS1 = "some-address1";

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @After
  public void after() throws Exception {
    if (vertices != null) {
      CountDownLatch latch = new CountDownLatch(vertices.length);
      for (Vertx vertx: vertices) {
        if (vertx != null) {
          vertx.close(ar -> {
            assertTrue(ar.succeeded());
            latch.countDown();
          });
        }
      }
      assertTrue(latch.await(30, TimeUnit.SECONDS));
    }

    FakeClusterManager.reset(); // Bit ugly
  }


  @Override
  protected <T> void testSend(T val, Consumer <T> consumer) {
    startNodes(2);
    registerCodecs();

    Registration reg = vertices[1].eventBus().registerHandler(ADDRESS1, (Message<T> msg) -> {
      if (consumer == null) {
        assertEquals(val, msg.body());
      } else {
        consumer.accept(msg.body());
      }
      testComplete();
    });
    reg.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[0].eventBus().send(ADDRESS1, val);
    });
    await();
  }

  @Override
  protected <T> void testReply(T val, Consumer<T> consumer) {
    startNodes(2);
    registerCodecs();
    String str = TestUtils.randomUnicodeString(1000);
    Registration reg = vertices[1].eventBus().registerHandler(ADDRESS1, msg -> {
      assertEquals(str, msg.body());
      msg.reply(val);
    });
    reg.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[0].eventBus().send(ADDRESS1, str, (Message<T> reply) -> {
        if (consumer == null) {
          assertEquals(val, reply.body());
        } else {
          consumer.accept(reply.body());
        }
        testComplete();
      });
    });

    await();
  }

  @Override
  protected <T> void testPublish(T val, Consumer<T> consumer) {
    int numNodes = 3;
    startNodes(numNodes);
    registerCodecs();

    AtomicInteger count = new AtomicInteger();
    class MyHandler implements Handler<Message<T>> {
      @Override
      public void handle(Message<T> msg) {
        if (consumer == null) {
          assertEquals(val, msg.body());
        } else {
          consumer.accept(msg.body());
        }
        if (count.incrementAndGet() == numNodes - 1) {
          testComplete();
        }
      }
    }
    AtomicInteger registerCount = new AtomicInteger(0);
    class MyRegisterHandler implements Handler<AsyncResult<Void>> {
      @Override
      public void handle(AsyncResult<Void> ar) {
        assertTrue(ar.succeeded());
        if (registerCount.incrementAndGet() == 2) {
          vertices[0].eventBus().publish(ADDRESS1, (T)val);
        }
      }
    }
    Registration reg = vertices[2].eventBus().registerHandler(ADDRESS1, new MyHandler());
    reg.completionHandler(new MyRegisterHandler());
    reg = vertices[1].eventBus().registerHandler(ADDRESS1, new MyHandler());
    reg.completionHandler(new MyRegisterHandler());
    vertices[0].eventBus().publish(ADDRESS1, (T)val);
    await();
  }

  @Test
  public void testLocalHandlerNotReceive() throws Exception {
    startNodes(2);
    vertices[1].eventBus().registerLocalHandler(ADDRESS1, msg -> {
      fail("Should not receive message");
    });
    vertices[0].eventBus().send(ADDRESS1, "foo");
    vertices[0].setTimer(1000, id -> testComplete());
    await();
  }

  // FIXME - these tests should test specifically with Copyable objects not just Object
  // Testing whether an Object with no codec can be sent and an object that is not Copyable/Shareable can be sent
  // should be separate tests
  // Also... need tests for replying with both objects with no codec and objects that are not Copyable/Shareable

  @Test
  public void testSendUnsupportedObject() {
    startNodes(1);
    EventBus eb = vertices[0].eventBus();
    try {
      eb.send(ADDRESS1, new Object());
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      eb.send(ADDRESS1, new HashMap<>());
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testSendWithReplyUnsupportedObject() {
    startNodes(1);
    EventBus eb = vertices[0].eventBus();
    try {
      eb.send(ADDRESS1, new Object(), reply -> {});
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      eb.send(ADDRESS1, new HashMap<>(), reply -> {});
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testSendWithTimeoutUnsupportedObject() {
    startNodes(1);
    EventBus eb = vertices[0].eventBus();
    try {
      eb.sendWithTimeout(ADDRESS1, new Object(), 1, reply -> {
      });
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      eb.sendWithTimeout(ADDRESS1, new HashMap<>(), 1, reply -> {
      });
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testUnregisterCodec() {
    startNodes(1);
    EventBus eb = vertices[0].eventBus();
    class Foo implements Shareable {
    }
    class FooCodec implements MessageCodec<Foo> {
      @Override
      public Buffer encode(Foo object) {
        return Buffer.newBuffer();
      }

      @Override
      public Foo decode(Buffer buffer) {
        return new Foo();
      }
    }

    eb.registerCodec(Foo.class, new FooCodec());
    eb.registerHandler("foo", (Message<Foo> msg) -> {
      eb.unregisterCodec(Foo.class);
      try {
        eb.send("bar", new Foo());
        fail("Should throw exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    eb.registerHandler("bar", (Message<Foo> msg) -> {
      fail("Should not be called");
    });

    eb.send("foo", new Foo());

    await();
  }

  private void startNodes(int numNodes) {
    CountDownLatch latch = new CountDownLatch(numNodes);
    vertices = new Vertx[numNodes];
    for (int i = 0; i < numNodes; i++) {
      int index = i;
      Vertx.newVertx(new VertxOptions().setClusterHost("localhost").setClusterPort(0).setClustered(true)
                            .setClusterManager(getClusterManager()), ar -> {
        assertTrue("Failed to start node", ar.succeeded());
        vertices[index] = ar.result();
        latch.countDown();
      });
    }
    try {
      assertTrue(latch.await(30, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      fail(e.getMessage());
    }
  }

  private void registerCodecs() {
    for (Vertx vertx : vertices) {
      registerCodecs(vertx.eventBus());
    }
  }

}
