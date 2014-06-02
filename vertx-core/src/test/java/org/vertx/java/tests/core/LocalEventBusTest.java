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

package org.vertx.java.tests.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.vertx.java.tests.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEventBusTest extends EventBusTestBase {

  private Vertx vertx;
  private EventBus eb;

  @Before
  public void before() throws Exception {
    vertx = VertxFactory.newVertx();
    eb = vertx.eventBus();
  }

  @After
  public void after() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.stop(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    assertTrue(latch.await(30, TimeUnit.SECONDS));
  }

  @Test
  public void testRegisterUnregister() {
    String str = randomUnicodeString(100);
    Handler<Message> handler = msg -> fail("Should not receive message");
    eb.registerHandler(ADDRESS1, handler);
    eb.unregisterHandler(ADDRESS1, handler);
    eb.send(ADDRESS1, str);
    vertx.setTimer(1000, id -> testComplete());
    await();
  }

  @Test
  public void testUnregisterTwice() {
    Handler<Message> handler = msg -> {};
    eb.registerHandler(ADDRESS1, handler);
    eb.unregisterHandler(ADDRESS1, handler);
    eb.unregisterHandler(ADDRESS1, handler);  // Ok to unregister twice
    testComplete();
  }

  @Test
  public void testUnregisterUnknown() {
    Handler<Message> handler = msg -> {};
    eb.unregisterHandler(ADDRESS1, handler);
    testComplete();
  }

  @Test
  public void testRegisterLocal() {
    String str = randomUnicodeString(100);
    eb.registerLocalHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    eb.send(ADDRESS1, str);
    await();
  }

  @Test
  public void testRegisterWithCompletionHandler() {
    String str = randomUnicodeString(100);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }, ar -> {
      assertTrue(ar.succeeded());
      eb.send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testSendRoundRobin() {
    String str = randomUnicodeString(100);
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
      eb.registerHandler(ADDRESS1, handlers[i]);
    }

    for (int i = 0; i < numMessages; i++) {
      eb.send(ADDRESS1, str);
    }

    await();
  }

  @Test
  public void testSendRegisterSomeUnregisterOne() {
    String str = randomUnicodeString(100);
    Handler<Message> handler1 = msg -> fail("Should not receive message");
    eb.registerHandler(ADDRESS1, handler1);
    AtomicInteger totalCount = new AtomicInteger();
    Handler<Message<String>> handler2 = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (totalCount.incrementAndGet() == 2) {
        testComplete();
      }
    };
    eb.registerHandler(ADDRESS1, handler2);
    Handler<Message<String>> handler3 = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (totalCount.incrementAndGet() == 2) {
        testComplete();
      }
    };
    eb.registerHandler(ADDRESS1, handler3);
    eb.unregisterHandler(ADDRESS1, handler1);
    eb.send(ADDRESS1, str);
    eb.send(ADDRESS1, str);
    await();
  }

  @Test
  public void testSendRegisterSameHandlerMultipleTimes() {
    String str = randomUnicodeString(100);
    AtomicInteger totalCount = new AtomicInteger();
    Handler<Message<String>> handler = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (totalCount.incrementAndGet() == 3) {
        testComplete();
      }
    };
    eb.registerHandler(ADDRESS1, handler);
    eb.registerHandler(ADDRESS1, handler);
    eb.registerHandler(ADDRESS1, handler);

    eb.send(ADDRESS1, str);
    eb.send(ADDRESS1, str);
    eb.send(ADDRESS1, str);
    await();
  }

  @Test
  public void testSendWithNoHandler() {
    eb.send(ADDRESS1, randomUnicodeString(100));
    vertx.setTimer(1000, id -> testComplete());
    await();
  }

  @Test
  public void testSendMultipleAddresses() {
    String str = randomUnicodeString(100);
    AtomicInteger cnt = new AtomicInteger();
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      fail("Should not receive message");
    });
    eb.registerHandler(ADDRESS2, (Message<String> msg) -> {
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
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    long timeout = 1000;
    eb.sendWithTimeout(ADDRESS1, str, timeout, ar -> {});
    await();
  }

  @Test
  public void testSendWithReply() {
    String str = randomUnicodeString(1000);
    String reply = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.reply(reply);
    });
    eb.send(ADDRESS1, str, (Message<String>msg) -> {
      assertEquals(reply, msg.body());
      testComplete();
    });
    await();
  }

  @Test
  public void testReplyToReply() {
    String str = randomUnicodeString(1000);
    String reply = randomUnicodeString(1000);
    String replyReply = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.reply(reply, (Message<String> rep) -> {
        assertEquals(replyReply, rep.body());
        testComplete();
      });
    });
    eb.send(ADDRESS1, str, (Message<String>msg) -> {
      assertEquals(reply, msg.body());
      msg.reply(replyReply);
    });
    await();
  }

  @Test
  public void testSendReplyWithTimeout() {
    String str = randomUnicodeString(1000);
    String reply = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      long start = System.currentTimeMillis();
      long timeout = 1000;
      msg.replyWithTimeout(reply, timeout, ar -> {
        long now = System.currentTimeMillis();
        assertFalse(ar.succeeded());
        Throwable cause = ar.cause();
        assertTrue(cause instanceof ReplyException);
        ReplyException re = (ReplyException)cause;
        assertEquals(-1, re.failureCode());
        assertEquals(ReplyFailure.TIMEOUT, re.failureType());
        assertTrue(now - start >= timeout);
        testComplete();
      });
    });
    eb.send(ADDRESS1, str, (Message<String>msg) -> {
      assertEquals(reply, msg.body());
      // Now don't reply
    });
    await();
  }

  @Test
  public void testSendReplyWithTimeoutNoTimeout() {
    String str = randomUnicodeString(1000);
    String reply = randomUnicodeString(1000);
    String replyReply = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      long timeout = 1000;
      msg.replyWithTimeout(reply, timeout, ar -> {
        assertTrue(ar.succeeded());
        assertEquals(replyReply, ar.result().body());
        testComplete();
      });
    });
    eb.send(ADDRESS1, str, (Message<String>msg) -> {
      assertEquals(reply, msg.body());
      msg.reply(replyReply);
    });
    await();
  }

  @Test
  public void testSendWithReplyDefaultTimeout() {
    long timeout = 1234;
    eb.setDefaultReplyTimeout(timeout);
    assertEquals(timeout, eb.getDefaultReplyTimeout());
    String str = randomUnicodeString(1000);
    String reply = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      // reply after timeout
      vertx.setTimer((long)(timeout * 1.5), id -> msg.reply(reply));
    });
    eb.send(ADDRESS1, str, (Message<String>msg) -> {
      fail("Should not be called");
    });
    vertx.setTimer(timeout * 2, id -> testComplete());
    await();
  }

  @Test
  public void testSendWithTimeoutNoTimeoutReply() {
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.reply(23);
    });
    long timeout = 1000;
    eb.sendWithTimeout(ADDRESS1, str, timeout, (AsyncResult<Message<Integer>> ar) -> {
      assertTrue(ar.succeeded());
      assertEquals(23, (int)ar.result().body());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutNoReply() {
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
    });
    long timeout = 1000;
    long start = System.currentTimeMillis();
    eb.sendWithTimeout(ADDRESS1, str, timeout, (AsyncResult<Message<Integer>> ar) -> {
      long now = System.currentTimeMillis();
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException)cause;
      assertEquals(-1, re.failureCode());
      assertEquals(ReplyFailure.TIMEOUT, re.failureType());
      assertTrue(now - start >= timeout);
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutNoHandlers() {
    String str = randomUnicodeString(1000);
    long timeout = 1000;
    eb.sendWithTimeout(ADDRESS1, str, timeout, (AsyncResult<Message<Integer>> ar) -> {
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException)cause;
      assertEquals(-1, re.failureCode());
      assertEquals(ReplyFailure.NO_HANDLERS, re.failureType());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutRecipientFailure() {
    String str = randomUnicodeString(1000);
    String failureMsg = randomUnicodeString(1000);
    int failureCode = 123;
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      msg.fail(failureCode, failureMsg);
    });
    long timeout = 1000;
    eb.sendWithTimeout(ADDRESS1, str, timeout, (AsyncResult<Message<Integer>> ar) -> {
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException)cause;
      assertEquals(failureCode, re.failureCode());
      assertEquals(failureMsg, re.getMessage());
      assertEquals(ReplyFailure.RECIPIENT_FAILURE, re.failureType());
      testComplete();
    });
    await();
  }

  @Test
  public void testSendWithTimeoutReplyAfterTimeout() {
    String str = randomUnicodeString(1000);
    long timeout = 1000;
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      vertx.setTimer((int)(timeout * 1.5), id -> {
        msg.reply("too late!");
      });
    });
    eb.sendWithTimeout(ADDRESS1, str, timeout, (AsyncResult<Message<Integer>> ar) -> {
      assertFalse(ar.succeeded());
      Throwable cause = ar.cause();
      assertTrue(cause instanceof ReplyException);
      ReplyException re = (ReplyException)cause;
      assertEquals(-1, re.failureCode());
      assertEquals(ReplyFailure.TIMEOUT, re.failureType());
      testComplete();
    });
    await();
  }

  // Sends with different types



  @Test
  public void testPublish() {
    String str = randomUnicodeString(100);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishMultipleHandlers() {
    String str = randomUnicodeString(100);
    AtomicInteger count = new AtomicInteger();
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
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
    String str = randomUnicodeString(1000);
    AtomicInteger count = new AtomicInteger();
    Handler<Message<String>> handler = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    };
    eb.registerHandler(ADDRESS1, handler);
    eb.registerHandler(ADDRESS1, handler);
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishMultipleHandlersUnregisterOne() {
    String str = randomUnicodeString(1000);
    Handler<Message<String>> handler1 = (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    };
    eb.registerHandler(ADDRESS1, handler1);
    Handler<Message<String>> handler2 = (Message<String> msg) -> {
      fail("Should not be called");
    };
    eb.registerHandler(ADDRESS1, handler2);
    eb.unregisterHandler(ADDRESS1, handler2);
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishMultipleHandlersDifferentAddresses() {
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    eb.registerHandler(ADDRESS2, (Message<String> msg) -> {
      fail("Should not receive message");
    });
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testCloseEventBus() {
    eb.close(ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Override
  protected <T> void testSend(T val, Consumer<T> consumer) {
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      if (consumer == null) {
        assertEquals(val, msg.body());
      } else {
        consumer.accept(msg.body());
      }
      testComplete();
    });
    eb.send(ADDRESS1, val);
    await();
  }

  @Override
  protected <T> void testSendNull(T obj) {
    eb.registerHandler(ADDRESS1,msg -> {
      assertNull(msg.body());
      testComplete();
    });
    eb.send(ADDRESS1, (T)null);
    await();
  }

  @Override
  protected <T> void testReply(T val, Consumer<T> consumer) {
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, msg -> {
      assertEquals(str, msg.body());
      msg.reply(val);
    });
    eb.send(ADDRESS1, str, (Message<T>reply) -> {
      if (consumer == null) {
        assertEquals(val, reply.body());
      } else {
        consumer.accept(reply.body());
      }
      testComplete();
    });
    await();
  }

  @Override
  protected <T> void testReplyNull(T val) {
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, msg -> {
      assertEquals(str, msg.body());
      msg.reply((T)null);
    });
    eb.send(ADDRESS1, str, (Message<T>reply) -> {
      assertNull(reply.body());
      testComplete();
    });
    await();
  }

  @Override
  protected <T> void testPublishNull(T val) {
    AtomicInteger count = new AtomicInteger();
    class MyHandler implements Handler<Message<T>> {
      @Override
      public void handle(Message<T> msg) {
        assertNull(msg.body());
        if (count.incrementAndGet() == 2) {
          testComplete();
        }
      }
    }
    eb.registerHandler(ADDRESS1, new MyHandler());
    eb.registerHandler(ADDRESS1, new MyHandler());
    eb.publish(ADDRESS1, (T)null);
    await();
  }

  @Override
  protected <T> void testPublish(T val, Consumer<T> consumer) {
    AtomicInteger count = new AtomicInteger();
    class MyHandler implements Handler<Message<T>> {
      @Override
      public void handle(Message<T> msg) {
        if (consumer == null) {
          assertEquals(val, msg.body());
        } else {
          consumer.accept(msg.body());
        }
        if (count.incrementAndGet() == 2) {
          testComplete();
        }
      }
    }
    eb.registerHandler(ADDRESS1, new MyHandler());
    eb.registerHandler(ADDRESS1, new MyHandler());
    eb.publish(ADDRESS1, (T)val);
    await();
  }



}

