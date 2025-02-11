/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.eventbus;

import io.vertx.core.*;
import io.vertx.core.eventbus.*;
import io.vertx.core.eventbus.impl.clustered.ClusteredEventBus;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.fakecluster.FakeClusterManager;
import io.vertx.tests.shareddata.AsyncMapTest.SomeClusterSerializableObject;
import io.vertx.tests.shareddata.AsyncMapTest.SomeSerializableObject;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import java.io.InvalidClassException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBusTest extends ClusteredEventBusTestBase {

  @Test
  public void testLocalHandlerNotVisibleRemotely() {
    startNodes(2);
    vertices[1].eventBus().localConsumer(ADDRESS1).handler(msg -> {
      fail("Should not receive message");
    });
    vertices[0].eventBus().send(ADDRESS1, "foo");
    vertices[0].eventBus().publish(ADDRESS1, "foo");
    vertices[0].setTimer(1000, id -> testComplete());
    await();
  }

  @Test
  public void testLocalHandlerClusteredSend() throws Exception {
    startNodes(2);
    waitFor(2);
    vertices[1].eventBus().consumer(ADDRESS1, msg -> complete()).completion().onComplete(v1 -> {
      vertices[0].eventBus().localConsumer(ADDRESS1, msg -> complete()).completion().onComplete(v2 -> {
        vertices[0].eventBus().send(ADDRESS1, "foo");
        vertices[0].eventBus().send(ADDRESS1, "foo");
      });
    });
    await();
  }

  @Test
  public void testLocalHandlerClusteredPublish() throws Exception {
    startNodes(2);
    waitFor(2);
    vertices[1].eventBus().consumer(ADDRESS1, msg -> complete()).completion().onComplete(v1 -> {
      vertices[0].eventBus().localConsumer(ADDRESS1, msg -> complete()).completion().onComplete(v2 -> {
        vertices[0].eventBus().publish(ADDRESS1, "foo");
      });
    });
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
    reg.completion().onComplete(ar -> {
      vertices[1].eventBus().send(ADDRESS1, myReplyException);
    });

    await();
  }

  // Make sure ping/pong works ok
  @Test
  public void testClusteredPong() throws Exception {
    VertxOptions options = new VertxOptions();
    options.getEventBusOptions().setClusterPingInterval(500).setClusterPingReplyInterval(500);
    startNodes(2, options);
    AtomicBoolean sending = new AtomicBoolean();
    MessageConsumer<String> consumer = vertices[0].eventBus().<String>consumer("foobar").handler(msg -> {
      if (!sending.get()) {
        sending.set(true);
        vertices[1].setTimer(4000, id -> {
          vertices[1].eventBus().send("foobar", "whatever2");
        });
      } else {
        testComplete();
      }
    });
    consumer.completion().onComplete(ar -> {
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
    consumer.completion().onComplete(v -> {
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
    consumer.completion().onComplete(v -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testSubsRemovedForClosedNode() throws Exception {
    testSubsRemoved(latch -> {
      vertices[1].close().onComplete(onSuccess(v -> {
        latch.countDown();
      }));
    });

  }

  @Test
  public void testSubsRemovedForKilledNode() throws Exception {
    testSubsRemoved(latch -> {
      VertxInternal vi = (VertxInternal) vertices[1];
      Promise<Void> promise = vi.getOrCreateContext().promise();
      vi.getClusterManager().leave(promise);
      promise.future().onComplete(onSuccess(v -> {
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
    }).completion().onComplete(onSuccess(v -> {
      vertices[1].eventBus().consumer(ADDRESS1, msg -> {
        fail("shouldn't get message");
      }).completion().onComplete(onSuccess(v2 -> {
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
    List<Integer> expected = Stream.iterate(0, i -> i + 1).limit(size).collect(Collectors.toList());
    ConcurrentLinkedDeque<Integer> obtained = new ConcurrentLinkedDeque<>();
    startNodes(2);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[1].eventBus().<Integer>consumer(ADDRESS1, msg -> {
      obtained.add(msg.body());
      if (obtained.size() == expected.size()) {
        assertEquals(expected, new ArrayList<>(obtained));
        testComplete();
      }
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    latch.await();
    EventBus bus = vertices[0].eventBus();
    expected.forEach(val -> bus.send(ADDRESS1, val));
    await();
  }

  @Test
  public void testSendLocalOnly() {
    testDeliveryOptionsLocalOnly(true);
  }

  @Test
  public void testPublishLocalOnly() {
    testDeliveryOptionsLocalOnly(false);
  }

  private void testDeliveryOptionsLocalOnly(boolean send) {
    waitFor(30);
    startNodes(2);
    AtomicLong localConsumer0 = new AtomicLong();
    vertices[0].eventBus().localConsumer(ADDRESS1).handler(msg -> {
      localConsumer0.incrementAndGet();
      complete();
    });
    AtomicLong consumer1 = new AtomicLong();
    vertices[1].eventBus().consumer(ADDRESS1).handler(msg -> {
      consumer1.incrementAndGet();
    }).completion().onComplete(onSuccess(v -> {
      for (int i = 0; i < 30; i++) {
        if (send) {
          vertices[0].eventBus().send(ADDRESS1, "msg", new DeliveryOptions().setLocalOnly(true));
        } else {
          vertices[0].eventBus().publish(ADDRESS1, "msg", new DeliveryOptions().setLocalOnly(true));
        }
      }
    }));
    await();
    assertEquals(30, localConsumer0.get());
    assertEquals(0, consumer1.get());
  }

  @Test
  public void testLocalOnlyDoesNotApplyToReplies() {
    startNodes(2);
    vertices[1].eventBus().consumer(ADDRESS1).handler(msg -> {
      msg.reply("pong", new DeliveryOptions().setLocalOnly(true));
    }).completion().onComplete(onSuccess(v -> {
      vertices[0].eventBus().request(ADDRESS1, "ping", new DeliveryOptions().setSendTimeout(500)).onComplete(onSuccess(msg -> testComplete()));
    }));
    await();
  }

  @Test
  public void testImmediateUnregistration() {
    startNodes(1);
    MessageConsumer<Object> consumer = vertices[0].eventBus().consumer(ADDRESS1);
    AtomicInteger completionCount = new AtomicInteger();
    consumer.completion().onComplete(v -> {
      // Do not assert success because the handler could be unregistered locally
      // before the registration was propagated to the cluster manager
      int val = completionCount.getAndIncrement();
      assertEquals(0, val);
    });
    consumer.handler(msg -> {});
    consumer.unregister().onComplete(onSuccess(v -> {
      int val = completionCount.getAndIncrement();
      assertEquals(1, val);
      testComplete();
    }));
    await();
  }

  @Test
  public void testSendWriteHandler() throws Exception {
    CountDownLatch updateLatch = new CountDownLatch(3);
    startNodes(2, () -> new WrappedClusterManager(getClusterManager()) {
      @Override
      public void registrationsUpdated(RegistrationUpdateEvent event) {
        super.registrationsUpdated(event);
        if (event.address().equals(ADDRESS1) && event.registrations().size() == 1) {
          updateLatch.countDown();
        }
      }
      @Override
      public boolean wantsUpdatesFor(String address) {
        return true;
      }
    });
    waitFor(2);
    vertices[1]
      .eventBus()
      .consumer(ADDRESS1, msg -> complete())
      .completion().onComplete(onSuccess(v1 -> updateLatch.countDown()));
    awaitLatch(updateLatch);
    MessageProducer<String> producer = vertices[0].eventBus().sender(ADDRESS1);
    producer.write("body").onComplete(onSuccess(v2 -> complete()));
    await();
  }

  @Test
  public void testSendWriteHandlerNoConsumer() {
    startNodes(2);
    MessageProducer<String> producer = vertices[0].eventBus().sender(ADDRESS1);
    producer.write("body").onComplete(onFailure(err -> {
      assertTrue(err instanceof ReplyException);
      ReplyException replyException = (ReplyException) err;
      assertEquals(-1, replyException.failureCode());
      testComplete();
    }));
    await();
  }

  @Test
  public void testPublishWriteHandler() {
    startNodes(2);
    waitFor(2);
    vertices[1]
      .eventBus()
      .consumer(ADDRESS1, msg -> complete())
      .completion().onComplete(onSuccess(v1 -> {
        MessageProducer<String> producer = vertices[0].eventBus().publisher(ADDRESS1);
        producer.write("body").onComplete(onSuccess(v -> complete()));
      }));
    await();
  }

  @Test
  public void testPublishWriteHandlerNoConsumer() {
    startNodes(2);
    MessageProducer<String> producer = vertices[0].eventBus().publisher(ADDRESS1);
    producer.write("body").onComplete(onFailure(err -> {
      assertTrue(err instanceof ReplyException);
      ReplyException replyException = (ReplyException) err;
      assertEquals(-1, replyException.failureCode());
      testComplete();
    }));
    await();
  }

  @Test
  public void testWriteHandlerConnectFailure() {
    VertxOptions options = getOptions();
    options.getEventBusOptions()
      .setSsl(true)
      .setTrustAll(false)
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    startNodes(2, options);
    vertices[1]
      .eventBus()
      .consumer(ADDRESS1, msg -> {})
      .completion().onComplete(onSuccess(v1 -> {
        MessageProducer<String> producer = vertices[0].eventBus().sender(ADDRESS1);
        producer.write("body").onComplete(onFailure(err -> {
          testComplete();
        }));
      }));
    await();
  }

  @Test
  public void testSelectorWantsUpdates() {
    WrappedClusterManager wrapped = new WrappedClusterManager(getClusterManager());
    startNodes(1, () -> wrapped);
    vertices[0].eventBus().consumer(ADDRESS1, msg -> {
      assertTrue(wrapped.wantsUpdatesFor(ADDRESS1));
      testComplete();
    }).completion().onComplete(onSuccess(v -> vertices[0].eventBus().send(ADDRESS1, "foo")));
    await();
  }

  @Test
  public void testSelectorDoesNotWantUpdates() {
    WrappedClusterManager wrapped = new WrappedClusterManager(getClusterManager());
    startNodes(1, () -> wrapped);
    assertFalse(wrapped.wantsUpdatesFor(ADDRESS1));
  }

  @Test
  public void testPublisherCanReceiveNoHandlersFailure() {
    startNodes(2);
    vertices[0].eventBus().publisher("foo").write("bar").onComplete(onFailure(t -> {
      if (t instanceof ReplyException) {
        ReplyException replyException = (ReplyException) t;
        assertEquals(ReplyFailure.NO_HANDLERS, replyException.failureType());
        testComplete();
      } else {
        fail();
      }
    }));
    await();
  }

  @Test
  public void testLocalConsumerNeverGetsMessagePublishedFromRemote() throws Exception {
    startNodes(2);
    waitFor(3);

    CountDownLatch completionLatch = new CountDownLatch(4);
    EventBus eb0 = vertices[0].eventBus();
    String firstAddress = "foo";
    eb0.localConsumer(firstAddress, message -> fail()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
    eb0.consumer(firstAddress, message -> complete()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
    String secondAddress = "bar";
    eb0.consumer(secondAddress, message -> complete()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
    eb0.localConsumer(secondAddress, message -> fail()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
    awaitLatch(completionLatch);

    EventBus eb1 = vertices[1].eventBus();
    eb1.publish(firstAddress, "content");
    eb1.publish(secondAddress, "content");

    vertx.setTimer(500, l -> complete()); // some delay to make sure no msg has been received by local consumers

    await();
  }

  @Test
  public void testLocalConsumerNeverGetsMessageSentFromRemote() throws Exception {
    startNodes(2);
    int maxMessages = 50;
    waitFor(4 * maxMessages);


    class CountingHandler implements Handler<Message<Object>> {
      AtomicInteger counter = new AtomicInteger();

      @Override
      public void handle(Message<Object> msg) {
        assertTrue(counter.incrementAndGet() <= maxMessages);
        complete();
      }
    }

    CountDownLatch completionLatch = new CountDownLatch(8);
    EventBus eb0 = vertices[0].eventBus();
    String firstAddress = "foo";
    for (int i = 0; i < 2; i++) {
      eb0.localConsumer(firstAddress, message -> fail()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
      eb0.consumer(firstAddress, new CountingHandler()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
    }
    String secondAddress = "bar";
    for (int i = 0; i < 2; i++) {
      eb0.consumer(secondAddress, new CountingHandler()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
      eb0.localConsumer(secondAddress, message -> fail()).completion().onComplete(onSuccess(v -> completionLatch.countDown()));
    }
    awaitLatch(completionLatch);

    EventBus eb1 = vertices[1].eventBus();
    String[] addresses = {firstAddress, secondAddress};
    for (int i = 0; i < 2 * maxMessages; i++) {
      for (String address : addresses) {
        eb1.send(address, "content");
      }
    }

    await();
  }

  @Test
  public void testRejectedClusterSerializableNotSent() {
    testRejectedNotSent(SomeClusterSerializableObject.class, new SomeClusterSerializableObject("bar"));
  }

  @Test
  public void testRejectedSerializableNotSent() {
    testRejectedNotSent(SomeSerializableObject.class, new SomeSerializableObject("bar"));
  }

  private <T> void testRejectedNotSent(Class<T> clazz, T message) {
    startNodes(2);
    vertices[0].eventBus()
      .clusterSerializableChecker(s -> Boolean.FALSE)
      .serializableChecker(s -> Boolean.FALSE);
    vertices[1].eventBus().consumer("foo", msg -> fail()).completion().onComplete(onSuccess(reg -> {
      try {
        vertices[0].eventBus().send("foo", message);
        fail();
      } catch (IllegalArgumentException e) {
        assertEquals("No message codec for type: class " + clazz.getName(), e.getMessage());
        testComplete();
      }
    }));
    await();
  }

  @Test
  public void testRejectedClusterSerializableNotReceived() {
    testRejectedNotReceived(SomeClusterSerializableObject.class, new SomeClusterSerializableObject("bar"));
  }

  @Test
  public void testRejectedSerializableNotReceived() {
    testRejectedNotReceived(SomeSerializableObject.class, new SomeSerializableObject("bar"));
  }

  private <T> void testRejectedNotReceived(Class<T> clazz, T message) {
    startNodes(2);
    vertices[1].eventBus()
      .clusterSerializableChecker(s -> Boolean.FALSE)
      .serializableChecker(s -> Boolean.FALSE);
    vertices[1].eventBus().consumer("foo", msg -> {
      try {
        Object body = msg.body();
        fail(String.valueOf(body));
      } catch (RuntimeException e) {
        Throwable cause = e.getCause();
        String exceptionMsg = cause instanceof InvalidClassException ? cause.getMessage() : e.getMessage();
        assertEquals("Class not allowed: " + clazz.getName(), exceptionMsg);
        testComplete();
      }
    }).completion().onComplete(onSuccess(reg -> {
      vertices[0].eventBus().send("foo", message);
    }));
    await();
  }

  @Test
  public void testMultiHeaders() {

    startNodes(2);
    waitFor(1);

    MultiMap expectedHeaders = MultiMap.caseInsensitiveMultiMap()
      .add("a", "1")
      .add("c", "2")
      .add("b", "3")
      .add("d", "4")
      .add("a", "5")
      .add("a", "6")
      .add("a", "7")
      .add("b", "8")
      .add("b", "9")
      .add("c", "10");

    vertices[1].eventBus().consumer(ADDRESS1, msg -> {

      MultiMap headers = msg.headers();
      assertEquals("headers should have expected size", 4, headers.size());
      assertEquals("headers should have expected number of entries", 10, headers.entries().size());
      assertEquals("entry 'a' should have 4 elements", Arrays.asList("1", "5", "6", "7"), headers.getAll("a"));
      assertEquals("entry 'b' should have 3 elements", Arrays.asList("3", "8", "9"), headers.getAll("b"));
      assertEquals("entry 'c' should have 2 elements", Arrays.asList("2", "10"), headers.getAll("c"));
      assertEquals("entry 'd' should have 1 element", Collections.singletonList("4"), headers.getAll("d"));
      complete();

    }).completion().onComplete(v1 -> {
      vertices[0].eventBus().send(ADDRESS1, "foo", new DeliveryOptions().setHeaders(expectedHeaders));
    });

    await();

  }

  @Test
  public void testPreserveMessageOrderingOnContext() {
    int num = 256;
    startNodes(2);
    ClusterManager clusterManager = ((VertxInternal) vertices[0]).getClusterManager();
    if (clusterManager instanceof FakeClusterManager) {
      // Other CM will exhibit latency for this one we must fake it
      FakeClusterManager fakeClusterManager = (FakeClusterManager) clusterManager;
      fakeClusterManager.getRegistrationsLatency(500);
    }
    AtomicInteger received = new AtomicInteger();
    vertices[1].eventBus().consumer(ADDRESS1, msg -> {
      int val = received.getAndIncrement();
      assertEquals(val, msg.body());
      if (val == num - 1) {
        testComplete();
      }
    });
    Context ctx = vertices[0].getOrCreateContext();
    ctx.runOnContext(v -> {
      for (int i = 0;i < num;i++) {
        vertices[0].eventBus().send(ADDRESS1, i);
      }
    });
    await();
  }

  @Test
  public void testSocketCleanup() {
    startNodes(1);
    vertices[0].eventBus().consumer(ADDRESS1, msg -> {
      msg.reply("pong");
    });
    AtomicInteger numberOfOutboundConnections = new AtomicInteger();
    AtomicInteger numberOfInboundConnections = new AtomicInteger();
    Vertx vertx = vertx(() -> Vertx.builder()
      .withClusterManager(getClusterManager())
      .withMetrics(options -> new VertxMetrics() {
        @Override
        public TCPMetrics<?> createNetClientMetrics(NetClientOptions options) {
          return new TCPMetrics<>() {
            @Override
            public Object connected(SocketAddress remoteAddress, String remoteName) {
              numberOfOutboundConnections.incrementAndGet();
              return null;
            }
            @Override
            public void disconnected(Object socketMetric, SocketAddress remoteAddress) {
              numberOfOutboundConnections.decrementAndGet();
            }
          };
        }
        @Override
        public TCPMetrics<?> createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
          return new TCPMetrics<>() {
            @Override
            public Object connected(SocketAddress remoteAddress, String remoteName) {
              numberOfInboundConnections.incrementAndGet();
              return null;
            }
            @Override
            public void disconnected(Object socketMetric, SocketAddress remoteAddress) {
              numberOfInboundConnections.decrementAndGet();
            }
          };
        }
      })
      .buildClustered()
      .await());
    vertx.eventBus().request(ADDRESS1, "ping").await();
    assertWaitUntil(() -> numberOfOutboundConnections.get() == 1 && numberOfInboundConnections.get() == 1);
    ClusteredEventBus eventBus = (ClusteredEventBus) vertices[0].eventBus();
    Future.future(eventBus::close).await();
    assertWaitUntil(() -> numberOfOutboundConnections.get() == 0 && numberOfInboundConnections.get() == 0);
  }
}
