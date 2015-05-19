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
public class ClusteredEventBusTest extends EventBusTestBase {

  private static final String ADDRESS1 = "some-address1";

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Override
  protected <T, R> void testSend(T val, R received, Consumer<T> consumer, DeliveryOptions options) {
    if (vertices == null) {
      startNodes(2);
    }

    MessageConsumer<T> reg = vertices[1].eventBus().<T>consumer(ADDRESS1).handler((Message<T> msg) -> {
      if (consumer == null) {
        assertEquals(received, msg.body());
        if (options != null) {
          assertNotNull(msg.headers());
          int numHeaders = options.getHeaders() != null ? options.getHeaders().size() : 0;
          assertEquals(numHeaders, msg.headers().size());
          if (numHeaders != 0) {
            for (Map.Entry<String, String> entry : options.getHeaders().entries()) {
              assertEquals(msg.headers().get(entry.getKey()), entry.getValue());
            }
          }
        }
      } else {
        consumer.accept(msg.body());
      }
      testComplete();
    });
    reg.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      if (options == null) {
        vertices[0].eventBus().send(ADDRESS1, val);
      } else {
        vertices[0].eventBus().send(ADDRESS1, val, options);
      }
    });
    await();
  }

  @Override
  protected <T> void testSend(T val, Consumer <T> consumer) {
    testSend(val, val, consumer, null);
  }

  @Override
  protected <T> void testReply(T val, Consumer<T> consumer) {
    testReply(val, val, consumer, null);
  }

  @Override
  protected <T, R> void testReply(T val, R received, Consumer<R> consumer, DeliveryOptions options) {
    if (vertices == null) {
      startNodes(2);
    }
    String str = TestUtils.randomUnicodeString(1000);
    MessageConsumer<?> reg = vertices[1].eventBus().consumer(ADDRESS1).handler(msg -> {
      assertEquals(str, msg.body());
      if (options == null) {
        msg.reply(val);
      } else {
        msg.reply(val, options);
      }
    });
    reg.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[0].eventBus().send(ADDRESS1, str, onSuccess((Message<R> reply) -> {
        if (consumer == null) {
          assertEquals(received, reply.body());
          if (options != null && options.getHeaders() != null) {
            assertNotNull(reply.headers());
            assertEquals(options.getHeaders().size(), reply.headers().size());
            for (Map.Entry<String, String> entry: options.getHeaders().entries()) {
              assertEquals(reply.headers().get(entry.getKey()), entry.getValue());
            }
          }
        } else {
          consumer.accept(reply.body());
        }
        testComplete();
      }));
    });

    await();
  }

  @Test
  public void testRegisterRemote1() {
    startNodes(2);
    String str = TestUtils.randomUnicodeString(100);
    vertices[0].eventBus().<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[1].eventBus().send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testRegisterRemote2() {
    startNodes(2);
    String str = TestUtils.randomUnicodeString(100);
    vertices[0].eventBus().consumer(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[1].eventBus().send(ADDRESS1, str);
    });
    await();
  }

  @Override
  protected <T> void testPublish(T val, Consumer<T> consumer) {
    int numNodes = 3;
    startNodes(numNodes);
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
    MessageConsumer reg = vertices[2].eventBus().<T>consumer(ADDRESS1).handler(new MyHandler());
    reg.completionHandler(new MyRegisterHandler());
    reg = vertices[1].eventBus().<T>consumer(ADDRESS1).handler(new MyHandler());
    reg.completionHandler(new MyRegisterHandler());
    vertices[0].eventBus().publish(ADDRESS1, val);
    await();
  }

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
      regLatch.countDown();
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
