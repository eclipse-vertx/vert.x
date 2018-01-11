/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBusTest extends ClusteredEventBusTestBase {

  @Test
  public void testLocalHandlerNotReceive() throws Exception {
    startNodes(2);
    vertices[1].eventBus().localConsumer(ADDRESS1).handler(msg -> {
      fail("Should not receive message");
    });
    vertices[0].eventBus().send(ADDRESS1, "foo");
    vertices[0].setTimer(1000, id -> testComplete());
    await();
  }

  @Test
  public void testDecoderSendAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    testSend(new MyPOJO(str), str, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderReplyAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    testReply(new MyPOJO(str), str, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderSendSymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testSend(pojo, pojo, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderReplySymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testReply(pojo, pojo, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDefaultDecoderSendAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    testSend(new MyPOJO(str), str, null, null);
  }

  @Test
  public void testDefaultDecoderReplyAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    testReply(new MyPOJO(str), str, null, null);
  }

  @Test
  public void testDefaultDecoderSendSymetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testSend(pojo, pojo, null, null);
  }

  @Test
  public void testDefaultDecoderReplySymetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testReply(pojo, pojo, null, null);
  }

  @Test
  public void testDefaultCodecReplyExceptionSubclass() throws Exception {
    startNodes(2);
    MyReplyException myReplyException = new MyReplyException(23, "my exception");
    MyReplyExceptionMessageCodec codec = new MyReplyExceptionMessageCodec();
    vertices[0].eventBus().registerDefaultCodec(MyReplyException.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyReplyException.class, codec);
    MessageConsumer<ReplyException> reg = vertices[0].eventBus().<ReplyException>consumer(ADDRESS1, msg -> {
      assertTrue(msg.body() instanceof MyReplyException);
      testComplete();
    });
    reg.completionHandler(ar -> {
      vertices[1].eventBus().send(ADDRESS1, myReplyException);
    });

    await();
  }

  // Make sure ping/pong works ok
  @Test
  public void testClusteredPong() throws Exception {
    startNodes(2, new VertxOptions().setClusterPingInterval(500).setClusterPingReplyInterval(500));
    AtomicBoolean sending = new AtomicBoolean();
    MessageConsumer<String> consumer = vertices[0].eventBus().<String>consumer("foobar").handler(msg -> {
      if (!sending.get()) {
        sending.set(true);
        vertx.setTimer(4000, id -> {
          vertices[1].eventBus().send("foobar", "whatever2");
        });
      } else {
        testComplete();
      }
    });
    consumer.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[1].eventBus().send("foobar", "whatever");
    });
    await();
  }

  @Test
  public void testConsumerHandlesCompletionAsynchronously1() {
    startNodes(2);
    MessageConsumer<Object> consumer = vertices[0].eventBus().consumer(ADDRESS1);
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    consumer.completionHandler(v -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      testComplete();
    });
    consumer.handler(msg -> {});
    await();
  }

  @Test
  public void testConsumerHandlesCompletionAsynchronously2() {
    startNodes(2);
    MessageConsumer<Object> consumer = vertices[0].eventBus().consumer(ADDRESS1);
    consumer.handler(msg -> {
    });
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    consumer.completionHandler(v -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testSubsRemovedForClosedNode() throws Exception {
    testSubsRemoved(latch -> {
      vertices[1].close(onSuccess(v -> {
        latch.countDown();
      }));
    });

  }

  @Test
  public void testSubsRemovedForKilledNode() throws Exception {
    testSubsRemoved(latch -> {
      VertxInternal vi = (VertxInternal)vertices[1];
      vi.getClusterManager().leave(onSuccess(v -> {
        latch.countDown();
      }));
    });

  }

  private void testSubsRemoved(Consumer<CountDownLatch> action) throws Exception {
    startNodes(3);
    CountDownLatch regLatch = new CountDownLatch(1);
    AtomicInteger cnt = new AtomicInteger();
    vertices[0].eventBus().consumer(ADDRESS1, msg -> {
      int c = cnt.getAndIncrement();
      assertEquals(msg.body(), "foo" + c);
      if (c == 9) {
        testComplete();
      }
      if (c > 9) {
        fail("too many messages");
      }
    }).completionHandler(onSuccess(v -> {
      vertices[1].eventBus().consumer(ADDRESS1, msg -> {
        fail("shouldn't get message");
      }).completionHandler(onSuccess(v2 -> {
        regLatch.countDown();
      }));
    }));
    awaitLatch(regLatch);

    CountDownLatch closeLatch = new CountDownLatch(1);
    action.accept(closeLatch);
    awaitLatch(closeLatch);

    // Allow time for kill to be propagate
    Thread.sleep(2000);

    vertices[2].runOnContext(v -> {
      // Now send some messages from node 2 - they should ALL go to node 0
      EventBus ebSender = vertices[2].eventBus();
      for (int i = 0; i < 10; i++) {
        ebSender.send(ADDRESS1, "foo" + i);
      }
    });

    await();

  }

  @Test
  public void sendNoContext() throws Exception {
    int size = 1000;
    ConcurrentLinkedDeque<Integer> expected = new ConcurrentLinkedDeque<>();
    ConcurrentLinkedDeque<Integer> obtained = new ConcurrentLinkedDeque<>();
    startNodes(2);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[1].eventBus().<Integer>consumer(ADDRESS1, msg -> {
      obtained.add(msg.body());
      if (obtained.size() == expected.size()) {
        assertEquals(new ArrayList<>(expected), new ArrayList<>(obtained));
        testComplete();
      }
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    latch.await();
    EventBus bus = vertices[0].eventBus();
    for (int i = 0;i < size;i++) {
      expected.add(i);
      bus.send(ADDRESS1, i);
    }
    await();
  }

}
