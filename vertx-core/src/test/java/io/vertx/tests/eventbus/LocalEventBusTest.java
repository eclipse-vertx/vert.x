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
import io.vertx.core.eventbus.impl.EventBusInternal;
import io.vertx.core.eventbus.impl.MessageConsumerImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.TestUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEventBusTest extends EventBusTestBase {

  private EventBusInternal eb;
  private boolean running;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    vertx.close();
    vertx = Vertx.vertx();
    eb = (EventBusInternal) vertx.eventBus();
    ImmutableObjectCodec immutableObjectCodec = new ImmutableObjectCodec();
    eb.registerCodec(immutableObjectCodec);
    eb.codecSelector(obj -> obj instanceof ImmutableObject ? immutableObjectCodec.name() : null);
    running = true;
  }

  @Override
  protected void tearDown() throws Exception {
    closeVertx();
    super.tearDown();
  }

  private void closeVertx() throws Exception {
    if (running) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close().onComplete(onSuccess(v -> {
        latch.countDown();
      }));
      assertTrue(latch.await(30, TimeUnit.SECONDS));
      running = false;
    }
  }

  @Override
  protected Vertx[] vertices(int num) {
    Vertx[] instances = new Vertx[num];
    Arrays.fill(instances, vertx);
    return instances;
  }

  @Test
  public void testDeliveryOptions() {
    DeliveryOptions options = new DeliveryOptions();

    assertIllegalArgumentException(() -> options.setSendTimeout(0));
    assertIllegalArgumentException(() -> options.setSendTimeout(-1));
    assertNullPointerException(() -> options.addHeader(null, ""));
    assertNullPointerException(() -> options.addHeader("", null));
  }

  @Test
  public void testArgumentValidation() throws Exception {
    assertNullPointerException(() -> eb.send(null, ""));
    assertNullPointerException(() -> eb.send(null, "", new DeliveryOptions()));
    assertNullPointerException(() -> eb.send("", "", null));
    assertNullPointerException(() -> eb.publish(null, ""));
    assertNullPointerException(() -> eb.publish(null, "", new DeliveryOptions()));
    assertNullPointerException(() -> eb.publish("", "", null));
    assertNullPointerException(() -> eb.consumer((String) null));
    assertNullPointerException(() -> eb.consumer((MessageConsumerOptions) null));
    assertNullPointerException(() -> eb.localConsumer(null));
    assertNullPointerException(() -> eb.sender(null));
    assertNullPointerException(() -> eb.sender(null, new DeliveryOptions()));
    assertNullPointerException(() -> eb.publisher("", null));
    assertNullPointerException(() -> eb.publisher(null, new DeliveryOptions()));
    assertNullPointerException(() -> eb.registerCodec(null));
    assertNullPointerException(() -> eb.unregisterCodec(null));
    assertNullPointerException(() -> eb.registerDefaultCodec(null, new MyPOJOEncoder1()));
    assertNullPointerException(() -> eb.registerDefaultCodec(Object.class, null));
    assertNullPointerException(() -> eb.unregisterDefaultCodec(null));
  }

  @Test
  public void testRegisterUnregister() {
    String str = TestUtils.randomUnicodeString(100);
    Handler<Message<String>> handler = msg -> fail("Should not receive message");
    MessageConsumer reg = eb.<String>consumer(ADDRESS1).handler(handler);
    assertEquals(ADDRESS1, reg.address());
    reg.unregister();
    eb.send(ADDRESS1, str);
    vertx.setTimer(1000, id -> testComplete());
    await();
  }

  @Test
  public void testUnregisterTwice() {
    Handler<Message<String>> handler = msg -> {};
    MessageConsumer reg = eb.<String>consumer(ADDRESS1).handler(handler);
    reg.unregister();
    reg.unregister(); // Ok to unregister twice
    testComplete();
  }

  @Test
  public void testMessageConsumerCloseHookIsClosedCorrectly() {
    Vertx vertx = Vertx.vertx();
    EventBus eb = vertx.eventBus();
    vertx.deployVerticle(new AbstractVerticle() {
      MessageConsumer consumer;
      @Override
      public void start() {
        context.exceptionHandler(err -> {
          fail("Unexpected exception");
        });
        consumer = eb.<String>consumer(ADDRESS1).handler(msg -> {});
      }
    }).onComplete(onSuccess(deploymentID -> {
      vertx.undeploy(deploymentID).onComplete(onSuccess(v -> {
        vertx.setTimer(10, id -> {
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testRegisterLocal1() {
    String str = TestUtils.randomUnicodeString(100);
    eb.<String>localConsumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      eb.send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testRegisterLocal2() {
    String str = TestUtils.randomUnicodeString(100);
    eb.localConsumer(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      eb.send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testRegisterWithCompletionHandler() {
    String str = TestUtils.randomUnicodeString(100);
    MessageConsumer<String> reg = eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    reg.completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      eb.send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testSendRoundRobin() {
    String str = TestUtils.randomUnicodeString(100);
    int numHandlers = 10;
    int numMessages = 100;
    Handler<Message<String>>[] handlers = new Handler[numHandlers];
    Map<Handler, Integer> countMap = new ConcurrentHashMap<>();
    AtomicInteger totalCount = new AtomicInteger();
    for (int i = 0; i < numHandlers; i++) {
      int index = i;
      handlers[i] = (Message<String> msg) -> {
        assertEquals(str, msg.body());
        Integer cnt = countMap.get(handlers[index]);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        countMap.put(handlers[index], icnt);
        if (totalCount.incrementAndGet() == numMessages) {
          assertEquals(numHandlers, countMap.size());
          for (Integer ind: countMap.values()) {
            assertEquals(numMessages / numHandlers, ind.intValue());
          }
          testComplete();
        }
      };
      eb.<String>consumer(ADDRESS1).handler(handlers[i]);
    }

    for (int i = 0; i < numMessages; i++) {
      eb.send(ADDRESS1, str);
    }

    await();
  }

  @Test
  public void testSendRegisterSomeUnregisterOne() {
    String str = TestUtils.randomUnicodeString(100);
    AtomicInteger totalCount = new AtomicInteger();
    Handler<Message<String>> handler1 = msg -> fail("Should not receive message");
    Handler<Message<String>> handler2 = msg -> {
      assertEquals(str, msg.body());
      if (totalCount.incrementAndGet() == 2) {
        testComplete();
      }
    };
    Handler<Message<String>> handler3 = msg -> {
      assertEquals(str, msg.body());
      if (totalCount.incrementAndGet() == 2) {
        testComplete();
      }
    };

    MessageConsumer reg = eb.<String>consumer(ADDRESS1).handler(handler1);
    eb.<String>consumer(ADDRESS1).handler(handler2);
    eb.<String>consumer(ADDRESS1).handler(handler3);
    reg.unregister();
    eb.send(ADDRESS1, str);
    eb.send(ADDRESS1, str);

    await();
  }

  @Test
  public void testSendRegisterSameHandlerMultipleTimes() {
    String str = TestUtils.randomUnicodeString(100);
    AtomicInteger totalCount = new AtomicInteger();
    Handler<Message<String>> handler = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (totalCount.incrementAndGet() == 3) {
        testComplete();
      }
    };
    eb.<String>consumer(ADDRESS1).handler(handler);
    eb.<String>consumer(ADDRESS1).handler(handler);
    eb.<String>consumer(ADDRESS1).handler(handler);

    eb.send(ADDRESS1, str);
    eb.send(ADDRESS1, str);
    eb.send(ADDRESS1, str);
    await();
  }

  @Test
  public void testSendWithNoHandler() {
    eb.send(ADDRESS1, TestUtils.randomUnicodeString(100));
    vertx.setTimer(1000, id -> testComplete());
    await();
  }

  @Test
  public void testSendMultipleAddresses() {
    String str = TestUtils.randomUnicodeString(100);
    AtomicInteger cnt = new AtomicInteger();
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      fail("Should not receive message");
    });
    eb.<String>consumer(ADDRESS2).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (cnt.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.send(ADDRESS2, str);
    eb.send(ADDRESS2, str);
    await();
  }

  @Test
  public void testSendWithTimeoutNoTimeoutNoReply() {
    String str = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    long timeout = 1000;
    eb.request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout));
    await();
  }

  @Test
  public void testSendWithReply() {
    String str = TestUtils.randomUnicodeString(1000);
    String reply = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.reply(reply);
    });
    eb.<String>request(ADDRESS1, str).onComplete(onSuccess((Message<String> msg) -> {
      assertEquals(reply, msg.body());
      testComplete();
    }));
    await();
  }

  @Test
  public void testReplyToReply() {
    String str = TestUtils.randomUnicodeString(1000);
    String reply = TestUtils.randomUnicodeString(1000);
    String replyReply = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.<String>replyAndRequest(reply).onComplete(onSuccess((Message<String> rep) -> {
        assertEquals(replyReply, rep.body());
        testComplete();
      }));
    });
    eb.<String>request(ADDRESS1, str).onComplete(onSuccess((Message<String>msg) -> {
      assertEquals(reply, msg.body());
      msg.reply(replyReply);
    }));
    await();
  }

  @Test
  public void testSendReplyWithTimeout() {
    String str = TestUtils.randomUnicodeString(1000);
    String reply = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      long start = System.currentTimeMillis();
      long timeout = 1000;
      msg.replyAndRequest(reply, new DeliveryOptions().setSendTimeout(timeout)).onComplete(onFailure(err -> {
        long now = System.currentTimeMillis();
        assertTrue(err instanceof ReplyException);
        ReplyException re = (ReplyException) err;
        assertEquals(-1, re.failureCode());
        assertEquals(ReplyFailure.TIMEOUT, re.failureType());
        assertTrue(now - start >= timeout);
        testComplete();
      }));
    });
    eb.<String>request(ADDRESS1, str).onComplete(onSuccess((Message<String>msg) -> {
      assertEquals(reply, msg.body());
      // Now don't reply
    }));
    await();
  }

  @Test
  public void testSendReplyWithTimeoutNoTimeout() {
    String str = TestUtils.randomUnicodeString(1000);
    String reply = TestUtils.randomUnicodeString(1000);
    String replyReply = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      long timeout = 1000;
      msg.replyAndRequest(reply, new DeliveryOptions().setSendTimeout(timeout)).onComplete(onSuccess(r -> {
        testComplete();
      }));
    });
    eb.<String>request(ADDRESS1, str).onComplete(onSuccess((Message<String>msg) -> {
      assertEquals(reply, msg.body());
      msg.reply(replyReply);
    }));
    await();
  }

  @Test
  public void testSendWithTimeoutNoTimeoutReply() {
    String str = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.reply(23);
    });
    long timeout = 1000;
    eb.<Integer>request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout)).onComplete((AsyncResult<Message<Integer>> ar) -> {
      assertTrue(ar.succeeded());
      assertEquals(23, (int) ar.result().body());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutNoReply() {
    String str = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
    });
    long timeout = 1000;
    long start = System.currentTimeMillis();
    eb.<Integer>request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout)).onComplete((AsyncResult<Message<Integer>> ar) -> {
      long now = System.currentTimeMillis();
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException) cause;
      assertEquals(-1, re.failureCode());
      assertEquals(ReplyFailure.TIMEOUT, re.failureType());
      assertTrue(now - start >= timeout);
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutNoHandlers() {
    String str = TestUtils.randomUnicodeString(1000);
    long timeout = 1000;
    eb.<Integer>request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout)).onComplete((AsyncResult<Message<Integer>> ar) -> {
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException) cause;
      assertEquals(-1, re.failureCode());
      assertEquals(ReplyFailure.NO_HANDLERS, re.failureType());
      assertEquals("No handlers for address " + ADDRESS1, re.getMessage());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutRecipientFailure() {
    String str = TestUtils.randomUnicodeString(1000);
    String failureMsg = TestUtils.randomUnicodeString(1000);
    int failureCode = 123;
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.fail(failureCode, failureMsg);
    });
    long timeout = 1000;
    eb.<Integer>request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout)).onComplete((AsyncResult<Message<Integer>> ar) -> {
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException) cause;
      assertEquals(failureCode, re.failureCode());
      assertEquals(failureMsg, re.getMessage());
      assertEquals(ReplyFailure.RECIPIENT_FAILURE, re.failureType());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutReplyAfterTimeout() {
    String str = TestUtils.randomUnicodeString(1000);
    long timeout = 1000;
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      vertx.setTimer((int)(timeout * 1.5), id -> {
        msg.reply("too late!");
      });
    });
    eb.<Integer>request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout)).onComplete((AsyncResult<Message<Integer>> ar) -> {
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException) cause;
      assertEquals(-1, re.failureCode());
      assertEquals(ReplyFailure.TIMEOUT, re.failureType());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutNoTimeoutAfterReply() {
    String str = TestUtils.randomUnicodeString(1000);
    long timeout = 1000;
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.reply("a reply");
    });
    AtomicBoolean received = new AtomicBoolean();
    eb.<Integer>request(ADDRESS1, str, new DeliveryOptions().setSendTimeout(timeout)).onComplete((AsyncResult<Message<Integer>> ar) -> {
      assertFalse(received.get());
      assertTrue(ar.succeeded());
      received.set(true);
      // Now wait longer than timeout and make sure we don't receive any other reply
      vertx.setTimer(timeout * 2, tid -> {
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testReplyToSendWithNoReplyHandler() {
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      msg.reply("a reply");
      testComplete();
    });
    eb.send(ADDRESS1, "whatever");
    await();
  }

  @Test
  public void testReplyToPublish() {
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      msg.reply("a reply");
      testComplete();
    });
    eb.publish(ADDRESS1, "whatever");
    await();
  }

  @Test
  public void testFailAfterSend() {
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      msg.fail(0, "a failure");
      testComplete();
    });
    eb.publish(ADDRESS1, "whatever");
    await();
  }

  @Test
  public void testFailAfterPublish() {
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      msg.fail(0, "a failure");
      testComplete();
    });
    eb.publish(ADDRESS1, "whatever");
    await();
  }

  // Sends with different types

  @Test
  public void testPublish() {
    String str = TestUtils.randomUnicodeString(100);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishMultipleHandlers() {
    String str = TestUtils.randomUnicodeString(100);
    AtomicInteger count = new AtomicInteger();
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishSameHandlerRegisteredTwice() {
    String str = TestUtils.randomUnicodeString(1000);
    AtomicInteger count = new AtomicInteger();
    Handler<Message<String>> handler = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    };
    eb.<String>consumer(ADDRESS1).handler(handler);
    eb.<String>consumer(ADDRESS1).handler(handler);
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishMultipleHandlersUnregisterOne() {
    String str = TestUtils.randomUnicodeString(1000);
    Handler<Message<String>> handler1 = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    };
    Handler<Message<String>> handler2 = (Message<String> msg) -> {
      fail("Should not be called");
    };

    eb.<String>consumer(ADDRESS1).handler(handler1);
    MessageConsumer reg = eb.<String>consumer(ADDRESS1).handler(handler2);
    reg.unregister();
    eb.publish(ADDRESS1, str);

    await();
  }

  @Test
  public void testPublishMultipleHandlersDifferentAddresses() {
    String str = TestUtils.randomUnicodeString(1000);
    eb.<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    eb.<String>consumer(ADDRESS2).handler((Message<String> msg) -> {
      fail("Should not receive message");
    });
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testNonRegisteredCodecType() {
    class Boom {
    }
    eb.consumer("foo").handler(msg -> {
      fail("Should not have gotten here");
    });

    try {
      eb.send("foo", new Boom());
    } catch (IllegalArgumentException e) {
      testComplete();
    }

    await();
  }


  @Test
  public void testCloseEventBus() {
    Promise<Void> promise = Promise.promise();
    eb.close(promise);
    promise.future().onComplete(ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testInVerticle() throws Exception {
    testInVerticle(false);
  }

  @Test
  public void testInWorkerVerticle() throws Exception {
    testInVerticle(true);
  }

  private void testInVerticle(boolean  worker) throws Exception {
    class MyVerticle extends AbstractVerticle {
      Context ctx;
      @Override
      public void start() {
        ctx = context;
        if (worker) {
          assertTrue(ctx.isWorkerContext());
        } else {
          assertTrue(ctx.isEventLoopContext());
        }
        Thread thr = Thread.currentThread();
        MessageConsumer<?> reg = vertx.eventBus().consumer(ADDRESS1).handler(msg -> {
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          msg.reply("bar");
        });
        reg.completion().onComplete(ar -> {
          assertTrue(ar.succeeded());
          assertSame(ctx, context);
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          vertx.eventBus().request(ADDRESS1, "foo").onComplete(onSuccess((Message<Object> reply) -> {
            assertSame(ctx, context);
            if (!worker) {
              assertSame(thr, Thread.currentThread());
            }
            assertEquals("bar", reply.body());
            testComplete();
          }));
        });
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(worker ? ThreadingModel.WORKER : ThreadingModel.EVENT_LOOP));
    await();
  }

  @Test
  public void testContextsSend() throws Exception {
    Set<ContextInternal> contexts = ConcurrentHashMap.newKeySet();
    CountDownLatch latch = new CountDownLatch(2);
    vertx.eventBus().consumer(ADDRESS1).handler(msg -> {
      msg.reply("bar");
      contexts.add(((VertxInternal) vertx).getContext());
      latch.countDown();
    });
    vertx.eventBus().request(ADDRESS1, "foo").onComplete(onSuccess((Message<Object> reply) -> {
      assertEquals("bar", reply.body());
      contexts.add(((VertxInternal) vertx).getContext());
      latch.countDown();
    }));
    awaitLatch(latch);
    assertEquals(2, contexts.size());
  }

  @Test
  public void testContextsPublish() throws Exception {
    Set<ContextInternal> contexts = ConcurrentHashMap.newKeySet();
    AtomicInteger cnt = new AtomicInteger();
    int numHandlers = 10;
    for (int i = 0; i < numHandlers; i++) {
      vertx.eventBus().consumer(ADDRESS1).handler(msg -> {
        contexts.add(((VertxInternal) vertx).getContext());
        if (cnt.incrementAndGet() == numHandlers) {
          assertEquals(numHandlers, contexts.size());
          testComplete();
        }
      });
    }
    vertx.eventBus().publish(ADDRESS1, "foo");
    await();
  }

  @Test
  public void testSelfSendDoesNotTrampoline() throws Exception {
    waitFor(2);
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      EventBus eb = vertx.eventBus();
      AtomicInteger received = new AtomicInteger();
      eb.consumer(ADDRESS1, msg -> {
        received.incrementAndGet();
        complete();
      });
      eb.send(ADDRESS1, "ping");
      assertEquals(0, received.get());
      complete();
    });
    await();
  }

  @Test
  public void testHeadersCopiedAfterSend() throws Exception {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add("foo", "bar");
    vertx.eventBus().consumer(ADDRESS1).handler(msg -> {
      assertNotSame(headers, msg.headers());
      assertEquals("bar", msg.headers().get("foo"));
      testComplete();
    });
    vertx.eventBus().send(ADDRESS1, "foo", new DeliveryOptions().setHeaders(headers));
    headers.remove("foo");
    await();
  }

  @Test
  public void testDecoderSendAsymmetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    testSend(new MyPOJO(str), str, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderReplyAsymmetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    testReply(new MyPOJO(str), str, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderSendSymmetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder2();
    vertx.eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testSend(pojo, pojo, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderReplySymmetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder2();
    vertx.eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testReply(pojo, pojo, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testNoRegisteredDecoder() throws Exception {
    try {
      vertx.eventBus().send(ADDRESS1, "foo", new DeliveryOptions().setCodecName("iqwjdoqiwd"));
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testRegisterSystemDecoder() throws Exception {
    try {
      vertx.eventBus().registerCodec(new MySystemDecoder());
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testUnregisterDecoder() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerCodec(codec);
    vertx.eventBus().unregisterCodec(codec.name());
    try {
      vertx.eventBus().send(ADDRESS1, new MyPOJO("foo"), new DeliveryOptions().setCodecName(codec.name()));
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testRegisterTwice() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerCodec(codec);
    try {
      vertx.eventBus().registerCodec(codec);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testCodecNullName() throws Exception {
    try {
      vertx.eventBus().registerCodec(new NullNameCodec());
      fail("Should throw exception");
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testDefaultDecoderSendAsymmetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    testSend(new MyPOJO(str), str, null, null);
  }

  @Test
  public void testDefaultDecoderReplyAsymmetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    testReply(new MyPOJO(str), str, null, null);
  }

  @Test
  public void testDefaultDecoderSendSymetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder2();
    vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testSend(pojo, pojo, null, null);
  }

  @Test
  public void testDefaultDecoderReplySymetric() throws Exception {
    MessageCodec codec = new MyPOJOEncoder2();
    vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testReply(pojo, pojo, null, null);
  }

  @Test
  public void testNoRegisteredDefaultDecoder() throws Exception {
    assertIllegalArgumentException(() -> vertx.eventBus().send(ADDRESS1, new MyPOJO("foo")));
  }

  @Test
  public void testRegisterDefaultSystemDecoder() throws Exception {
    assertIllegalArgumentException(() -> vertx.eventBus().registerDefaultCodec(MyPOJO.class, new MySystemDecoder()));
  }

  @Test
  public void testUnregisterDefaultDecoder() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertx.eventBus().unregisterDefaultCodec(MyPOJO.class);
    assertIllegalArgumentException(() -> vertx.eventBus().send(ADDRESS1, new MyPOJO("foo")));
  }

  @Test
  public void testRegisterDefaultTwice() throws Exception {
    MessageCodec codec = new MyPOJOEncoder1();
    vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec);
    assertIllegalStateException(() -> vertx.eventBus().registerDefaultCodec(MyPOJO.class, codec));
  }

  @Test
  public void testDefaultCodecNullName() throws Exception {
    assertNullPointerException(() -> vertx.eventBus().registerDefaultCodec(String.class, new NullNameCodec()));
  }

  @Test
  public void testDefaultCodecReplyExceptionSubclass() throws Exception {
    MyReplyException myReplyException = new MyReplyException(23, "my exception");
    vertx.eventBus().registerDefaultCodec(MyReplyException.class, new MyReplyExceptionMessageCodec());
    eb.<ReplyException>consumer(ADDRESS1, msg -> {
      assertTrue(msg.body() instanceof MyReplyException);
      testComplete();
    });
    vertx.eventBus().send(ADDRESS1, myReplyException);
    await();
  }


  @Override
  protected boolean shouldImmutableObjectBeCopied() {
    return false;
  }

  @Test
  public void testPauseResumeMessageStream() {
    testPauseResume((consumer, handler) -> consumer.handler(message -> handler.handle(message.body())));
  }

  @Test
  public void testPauseResumeBodyStream() {
    testPauseResume((consumer, handler) -> consumer.bodyStream().handler(handler));
  }

  private void testPauseResume(BiFunction<MessageConsumer<String>, Handler<String>, ReadStream<?>> register) {
    String[] data = new String[11];
    for (int i = 0;i < data.length;i++) {
      data[i] = TestUtils.randomAlphaString(10);
    }
    Set<String> expected = new HashSet<>();
    Handler<String> handler = body -> {
      assertTrue("Was expecting " + expected + " to contain " + body, expected.remove(body));
      if (expected.isEmpty()) {
        testComplete();
      }
    };
    MessageConsumer<String> reg = eb.<String>consumer(new MessageConsumerOptions().setAddress(ADDRESS1).setMaxBufferedMessages(10));
    ReadStream<?> controller = register.apply(reg, handler);
    ((MessageConsumerImpl<String>) reg).discardHandler(msg -> {
      assertEquals(data[10], msg.body());
      expected.addAll(Arrays.asList(data).subList(0, 10));
      controller.resume();
    });
    controller.pause();
    for (String msg : data) {
      eb.publish(ADDRESS1, msg);
    }
    await();
  }

  @Test
  public void testPauseFetchMessageStream() throws Exception {
    testPauseFetch((consumer, handler) -> consumer.handler(message -> handler.handle(message.body())));
  }

  @Test
  public void testPauseFetchBodyStream() throws Exception {
    testPauseFetch((consumer, handler) -> consumer.bodyStream().handler(handler));
  }

  private void testPauseFetch(BiFunction<MessageConsumer<String>, Handler<String>, ReadStream<?>> streamSupplier) throws Exception {
    List<String> data = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      data.add(TestUtils.randomAlphaString(10));
    }
    List<String> received = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch receiveLatch = new CountDownLatch(4);
    MessageConsumerImpl<String> consumer = (MessageConsumerImpl<String>) eb.<String>consumer(new MessageConsumerOptions().setAddress(ADDRESS1).setMaxBufferedMessages(5));
    streamSupplier.apply(consumer, e -> {
      received.add(e);
      receiveLatch.countDown();
    }).pause().fetch(4);
    List<String> discarded = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch discardLatch = new CountDownLatch(2);
    consumer.discardHandler(msg -> {
      discarded.add(msg.body());
      discardLatch.countDown();
    });
    ListIterator<String> iterator = data.listIterator();
    while (iterator.nextIndex() < 4) {
      eb.publish(ADDRESS1, iterator.next());
    }
    awaitLatch(receiveLatch);
    while (iterator.hasNext()) {
      eb.publish(ADDRESS1, iterator.next());
    }
    awaitLatch(discardLatch);
    assertEquals(data.subList(0, 4), received);
    assertEquals(data.subList(data.size() - 2, data.size()), discarded);
  }

  @Test
  public void testExceptionWhenDeliveringBufferedMessageWithMessageStream() {
    testExceptionWhenDeliveringBufferedMessage((consumer, handler) -> consumer.handler(message -> handler.handle(message.body())));
  }

  @Test
  public void testExceptionWhenDeliveringBufferedMessageWithBodyStream() {
    testExceptionWhenDeliveringBufferedMessage((consumer, handler) -> consumer.bodyStream().handler(handler));
  }

  private void testExceptionWhenDeliveringBufferedMessage(BiFunction<MessageConsumer<String>, Handler<String>, ReadStream<?>> register) {
    String[] data = new String[11];
    for (int i = 0;i < data.length;i++) {
      data[i] = TestUtils.randomAlphaString(10);
    }
    Set<String> expected = new HashSet<>();
    Handler<String> handler = body -> {
      assertTrue("Was expecting " + expected + " to contain " + body, expected.remove(body));
      if (expected.isEmpty()) {
        testComplete();
      } else {
        throw new RuntimeException();
      }
    };
    MessageConsumer<String> reg = eb.<String>consumer(new MessageConsumerOptions().setAddress(ADDRESS1).setMaxBufferedMessages(10));
    ReadStream<?> controller = register.apply(reg, handler);
    ((MessageConsumerImpl<String>) reg).discardHandler(msg -> {
      assertEquals(data[10], msg.body());
      expected.addAll(Arrays.asList(data).subList(0, 10));
      controller.resume();
    });
    controller.pause();
    for (String msg : data) {
      eb.publish(ADDRESS1, msg);
    }
    await();
  }

  @Test
  public void testUnregisterationOfRegisteredConsumerCallsEndHandlerWithMessageStream() {
    MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
    testUnregisterationOfRegisteredConsumerCallsEndHandler(consumer, consumer);
  }

  @Test
  public void testUnregisterationOfRegisteredConsumerCallsEndHandlerWithBodyStream() {
    MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
    testUnregisterationOfRegisteredConsumerCallsEndHandler(consumer, consumer.bodyStream());
  }

  private void testUnregisterationOfRegisteredConsumerCallsEndHandler(MessageConsumer<String> consumer, ReadStream<?> readStream) {
    consumer.handler(msg -> {});
    consumer.endHandler(v -> testComplete());
    consumer.unregister();
    await();
  }

  @Test
  public void testUnregisterThenUnsetEndHandler() {
    MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
    consumer.endHandler(v -> {});
    consumer.unregister().onComplete(res -> {
      testComplete();
    });
    consumer.endHandler(null);
    await();
  }

  @Test
  public void testUnregistrationWhenSettingNullHandlerWithConsumer() {
    MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
    testUnregistrationWhenSettingNullHandler(consumer, consumer);
  }

  @Test
  public void testUnregistrationWhenSettingNullHandlerWithBodyStream() {
    MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
    testUnregistrationWhenSettingNullHandler(consumer, consumer.bodyStream());
  }

  private void testUnregistrationWhenSettingNullHandler(MessageConsumer<String> consumer, ReadStream<?> readStream) {
    readStream.handler(msg -> {});
    assertTrue(consumer.isRegistered());
    readStream.handler(null);
    assertFalse(consumer.isRegistered());
  }

  @Test
  public void testSender() {
    String str = TestUtils.randomUnicodeString(100);
    MessageProducer<String> sender = eb.sender(ADDRESS1);
    eb.consumer(ADDRESS1).handler(message -> {
      if (message.body().equals(str)) {
        testComplete();
      }
    });
    sender.write(str);
    await();
  }

  @Test
  public void testSenderWithOptions() {
    String str = TestUtils.randomUnicodeString(100);
    MessageProducer<String> sender = eb.sender(ADDRESS1, new DeliveryOptions().addHeader("foo", "foo_value"));
    eb.consumer(ADDRESS1).handler(message -> {
      if (message.body().equals(str) && "foo_value".equals(message.headers().get("foo"))) {
        testComplete();
      }
    });
    sender.write(str);
    await();
  }

  @Test
  public void testPublisher() {
    String str = TestUtils.randomUnicodeString(100);
    MessageProducer<String> publisher = eb.publisher(ADDRESS1);
    assertEquals(ADDRESS1, publisher.address());
    AtomicInteger count = new AtomicInteger();
    int n = 2;
    for (int i = 0;i < n;i++) {
      eb.consumer(ADDRESS1).handler(message -> {
        if (message.body().equals(str) && count.incrementAndGet() == n) {
          testComplete();
        }
      });
    }
    publisher.write(str);
    await();
  }

  @Test
  public void testPublisherWithOptions() {
    String str = TestUtils.randomUnicodeString(100);
    MessageProducer<String> publisher = eb.publisher(ADDRESS1, new DeliveryOptions().addHeader("foo", "foo_value"));
    assertEquals(ADDRESS1, publisher.address());
    AtomicInteger count = new AtomicInteger();
    int n = 2;
    for (int i = 0;i < n;i++) {
      eb.consumer(ADDRESS1).handler(message -> {
        if (message.body().equals(str) && "foo_value".equals(message.headers().get("foo")) && count.incrementAndGet() == n) {
          testComplete();
        }
      });
    }
    publisher.write(str);
    await();
  }

  @Test
  public void testCloseSender1() {
    eb.sender(ADDRESS1).close();
  }

  @Test
  public void testClosePublisher1() {
    eb.publisher(ADDRESS1).close();
  }

  @Test
  public void testConsumerHandlesCompletionAsynchronously() {
    MessageConsumer<Object> consumer = eb.consumer(ADDRESS1);
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    consumer.completion().onComplete(v -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      testComplete();
    });
    consumer.handler(msg -> {
    });
    await();
  }

  @Test
  public void testConsumerHandlesCompletionAsynchronously2() {
    MessageConsumer<Object> consumer = eb.consumer(ADDRESS1);
    consumer.handler(msg -> {
    });
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    consumer.completion().onComplete(v -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testUpdateDeliveryOptionsOnProducer() {
    MessageProducer<String> producer = eb.sender(ADDRESS1);
    MessageConsumer<String> consumer = eb.<String>consumer(ADDRESS1);
    consumer.completion().onComplete(v -> {
      assertTrue(v.succeeded());
      producer.write("no-header");
    });
    consumer.handler(msg -> {
      String body = msg.body();
      assertNotNull(body);
      switch (body) {
        case "no-header":
          assertNull(msg.headers().get("header-name"));
          producer.deliveryOptions(new DeliveryOptions().addHeader("header-name", "header-value"));
          producer.write("with-header");
          break;
        case "with-header":
          assertEquals("header-value", msg.headers().get("header-name"));
          testComplete();
          break;
        default:
          fail();
      }
    });
    await();
  }

  @Test
  public void testCloseCallsEndHandlerWithRegistrationContext() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    CountDownLatch registered = new CountDownLatch(1);
    ctx.runOnContext(v1 -> {
      MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
      consumer.endHandler(v2 -> {
        assertSame(Vertx.currentContext(), ctx);
        testComplete();
      });
      consumer.handler(msg -> {});
      consumer.completion().onComplete(ar -> {
        assertTrue(ar.succeeded());
        registered.countDown();
      });
    });
    awaitLatch(registered);
    closeVertx();
    await();
  }

  @Test
  public void testConsumerUnregisterDoesNotCancelTimer0() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      // The delay does not matter so much, it will always be executed after this task anyway
      vertx.setTimer(50, id -> {
        assertEquals(0, (long) id);
        testComplete();
      });
      eb.consumer(ADDRESS1).unregister();
    });
    await();
  }

  @Test
  public void testUnregisterConsumerDiscardPendingMessages() {
    MessageConsumer<String> consumer = eb.consumer(ADDRESS1);
    consumer.handler(msg -> {
      assertEquals("val0", msg.body());
      consumer.pause();
      eb.send(ADDRESS1, "val1");
      Context ctx = Vertx.currentContext();
      ctx.runOnContext(v -> {
        consumer.resume();
        ((MessageConsumerImpl<?>) consumer).discardHandler(discarded -> {
          assertEquals("val1", discarded.body());
          testComplete();
        });
        consumer.handler(null);
      });
    });
    eb.send(ADDRESS1, "val0");
    await();
  }

  @Test
  public void testImmediateUnregistration() {
    MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1);
    AtomicInteger completionCount = new AtomicInteger();
    consumer.completion().onComplete(ar -> {
      int val = completionCount.getAndIncrement();
      assertEquals(0, val);
      assertTrue(ar.succeeded());
      vertx.setTimer(10, id -> {
        testComplete();
      });
    });
    consumer.handler(msg -> {});
    consumer.unregister();
    await();
  }

  @Test
  public void testSendWriteHandler() {
    waitFor(2);
    eb.consumer(ADDRESS1, msg -> complete());
    MessageProducer<String> producer = eb.sender(ADDRESS1);
    producer.write("body").onComplete(onSuccess(v -> complete()));
    await();
  }

  @Test
  public void testSendWriteHandlerNoConsumer() {
    MessageProducer<String> producer = eb.sender(ADDRESS1);
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
    waitFor(2);
    eb.consumer(ADDRESS1, msg -> complete());
    MessageProducer<String> producer = eb.publisher(ADDRESS1);
    producer.write("body").onComplete(onSuccess(v -> complete()));
    await();
  }

  @Test
  public void testPublishWriteHandlerNoConsumer() {
    MessageProducer<String> producer = eb.publisher(ADDRESS1);
    producer.write("body").onComplete(onFailure(err -> {
      assertTrue(err instanceof ReplyException);
      ReplyException replyException = (ReplyException) err;
      assertEquals(-1, replyException.failureCode());
      testComplete();
    }));
    await();
  }

  @Test
  public void testClosePublisher() {
    MessageProducer<String> producer = eb.publisher(ADDRESS1);
    producer.close().onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testCloseSender() {
    MessageProducer<String> producer = eb.sender(ADDRESS1);
    producer.close().onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Ignore
  @Test
  public void testEarlyTimeoutOfBufferedMessagesOnHandlerUnregistration() {
    testEarlyTimeoutOfBufferedMessages(
      MessageConsumer::pause,
      MessageConsumer::unregister);
  }

  private void testEarlyTimeoutOfBufferedMessages(Consumer<MessageConsumer<?>> beforeRequest, Consumer<MessageConsumer<?>> afterRequest) {
    DeliveryOptions noTimeout = new DeliveryOptions().setSendTimeout(Long.MAX_VALUE);
    MessageConsumer<?> consumer = vertx.eventBus().consumer(new MessageConsumerOptions().setAddress(ADDRESS1).setMaxBufferedMessages(1));
    consumer.handler(message -> message.reply(null));
    ((MessageConsumerImpl<?>)consumer).discardHandler(msg -> {
      if (msg.body().equals(2)) {
        afterRequest.accept(consumer);
      }
    });
    consumer.completion().onComplete(__ -> {
      beforeRequest.accept(consumer);
      vertx.eventBus().request(ADDRESS1, 1, noTimeout).onComplete(onFailure(err -> {
        assertTrue(err instanceof ReplyException);
        assertEquals(ReplyFailure.TIMEOUT, ((ReplyException) err).failureType());
        testComplete();
      }));
      vertx.eventBus().send(ADDRESS1, 2);
    });
    await();
  }

  @Ignore
  @Test
  public void testEarlyTimeoutWhenMaxBufferedMessagesExceeded() {
    DeliveryOptions noTimeout = new DeliveryOptions().setSendTimeout(Long.MAX_VALUE);
    MessageConsumer<?> consumer = vertx.eventBus().consumer(new MessageConsumerOptions().setAddress(ADDRESS1).setMaxBufferedMessages(0));
    consumer.handler(message -> fail());
    consumer.completion().onComplete(__ -> {
      consumer.pause();
      vertx.eventBus().request(ADDRESS1, 1, noTimeout).onComplete(onFailure(err -> {
        assertTrue(err instanceof ReplyException);
        assertEquals(ReplyFailure.TIMEOUT, ((ReplyException) err).failureType());
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testEarlyTimeoutOnHandlerUnregistration() {
    DeliveryOptions noTimeout = new DeliveryOptions().setSendTimeout(Long.MAX_VALUE);
    MessageConsumer<?> consumer = vertx.eventBus().consumer(ADDRESS1);
    consumer.handler(message -> fail());
    consumer.completion().onComplete(__ -> {
      vertx.eventBus().request(ADDRESS1, 1, noTimeout).onComplete(onFailure(err -> {
        assertTrue(err instanceof ReplyException);
        assertEquals(ReplyFailure.TIMEOUT, ((ReplyException) err).failureType());
        testComplete();
      }));
      consumer.unregister();
    });
    await();
  }

  @Test
  public void testMessageConsumptionStayOnWorkerThreadAfterResume() {
    ContextInternal worker = ((VertxInternal) vertx).createWorkerContext();
    worker.runOnContext(v -> {
      EventBus bus = vertx.eventBus();
      AtomicBoolean enabled = new AtomicBoolean(false);
      MessageConsumer<String> consumer = bus.consumer(ADDRESS1, msg -> {
        assertTrue(enabled.get());
        assertTrue(worker.inThread());
        testComplete();
      });
      consumer.pause();
      MessageProducer<String> sender = bus.sender(ADDRESS1);
      sender.write("msg").onComplete(onSuccess(v2 -> {
        worker.runOnContext(v3 -> {
          enabled.set(true);
          consumer.resume();
        });
      }));
    });
    await();
  }

  @Test
  public void testUnregisterInHandler() {
    waitFor(2);
    MessageConsumerImpl<Object> consumer = (MessageConsumerImpl<Object>) vertx.eventBus().consumer(ADDRESS1);
    consumer.discardHandler(msg -> {
      assertEquals("msg-2", msg.body());
      complete();
    });
    consumer.handler(msg -> {
      consumer.unregister();
      vertx.runOnContext(v -> {
        complete();
      });
    });
    consumer.pause();
    vertx.eventBus().send(ADDRESS1, "msg-1");
    vertx.eventBus().send(ADDRESS1, "msg-2");
    vertx.runOnContext(v -> {
      consumer.resume();
    });
    await();
  }
}
