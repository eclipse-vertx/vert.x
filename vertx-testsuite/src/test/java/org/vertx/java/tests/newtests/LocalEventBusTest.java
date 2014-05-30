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

package org.vertx.java.tests.newtests;

import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.vertx.java.tests.newtests.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEventBusTest extends VertxTestBase {

  private EventBus eb;

  @Before
  public void before() throws Exception {
    eb = vertx.eventBus();
  }

  private static final String ADDRESS1 = "some-address1";
  private static final String ADDRESS2 = "some-address2";

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
  public void testSendString() {
    String str = randomUnicodeString(100);
    testSend(str);
  }

  @Test
  public void testSendNullString() {
    testSendNull((String)null);
  }

  @Test
  public void testReplyString() {
    String str = randomUnicodeString(100);
    testReply(str);
  }

  @Test
  public void testReplyNullString() {
    testReplyNull((String) null);
  }

  @Test
  public void testPublishString() {
    String str = randomUnicodeString(100);
    testPublish(str);
  }

  @Test
  public void testPublishNullString() {
    testPublishNull((String)null);
  }

  @Test
  public void testSendBooleanTrue() {
    testSend(true);
  }

  @Test
  public void testSendBooleanFalse() {
    testSend(false);
  }

  @Test
  public void testSendNullBoolean() {
    testSendNull((Boolean)null);
  }

  @Test
  public void testReplyBooleanTrue() {
    testReply(true);
  }

  @Test
  public void testReplyBooleanFalse() {
    testReply(false);
  }

  @Test
  public void testReplyNullBoolean() {
    testReplyNull((Boolean) null);
  }

  @Test
  public void testPublishBooleanTrue() {
    testPublish(true);
  }

  @Test
  public void testPublishBooleanFalse() {
    testPublish(false);
  }

  @Test
  public void testPublishNullBoolean() {
    testPublishNull((Boolean) null);
  }

  @Test
  public void testSendBuffer() {
    Buffer sent = randomBuffer(100);
    testSendWithAsserter(sent, (buffer) -> {
      buffersEqual(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testSendNullBuffer() {
    testSendNull((Buffer)null);
  }

  @Test
  public void testReplyBuffer() {
    Buffer sent = randomBuffer(100);
    testReplyWithAsserter(sent, (bytes) -> {
      buffersEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testReplyNullBuffer() {
    testReplyNull((Buffer) null);
  }

  @Test
  public void testPublishBuffer() {
    Buffer sent = randomBuffer(100);
    testPublishWithAsserter(sent, (buffer) -> {
      buffersEqual(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testPublishNullBuffer() {
    testPublishNull((Buffer)null);
  }

  @Test
  public void testSendByte() {
    testSend(randomByte());
  }

  @Test
  public void testSendNullByte() {
    testSendNull((Byte) null);
  }

  @Test
  public void testReplyByte() {
    testReply(randomByte());
  }

  @Test
  public void testReplyNullByte() {
    testReplyNull((Byte) null);
  }

  @Test
  public void testPublishByte() {
    testPublish(randomByte());
  }

  @Test
  public void testPublishNullByte() {
    testPublish((Byte)null);
  }

  @Test
  public void testSendByteArray() {
    byte[] sent = randomByteArray(100);
    testSendWithAsserter(sent, (bytes) -> {
      byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testSendNullByteArray() {
    testSendNull((byte[])null);
  }

  @Test
  public void testReplyByteArray() {
    byte[] sent = randomByteArray(100);
    testReplyWithAsserter(sent, (bytes) -> {
      byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testReplyNullByteArray() {
    testReplyNull((byte[]) null);
  }

  @Test
  public void testPublishByteArray() {
    byte[] sent = randomByteArray(100);
    testPublishWithAsserter(sent, (bytes) -> {
      byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testPublishNullByteArray() {
    testPublishNull((byte[])null);
  }

  @Test
  public void testSendCharacter() {
    testSend('Y');
  }

  @Test
  public void testSendNullCharacter() {
    testSendNull((Character)null);
  }

  @Test
  public void testReplyCharacter() {
    testReply('Y');
  }

  @Test
  public void testReplyNullCharacter() {
    testReplyNull((Character) null);
  }

  @Test
  public void testPublishCharacter() {
    testPublish('Y');
  }

  @Test
  public void testPublishNullCharacter() {
    testPublishNull((Character)null);
  }

  @Test
  public void testSendDouble() {
    testSend(134.456d);
  }

  @Test
  public void testSendNullDouble() {
    testSendNull((Double)null);
  }

  @Test
  public void testReplyDouble() {
    testReply(134.456d);
  }

  @Test
  public void testReplyNullDouble() {
    testReplyNull((Double) null);
  }

  @Test
  public void testPublishDouble() {
    testPublish(134.56d);
  }

  @Test
  public void testPublishNullDouble() {
    testPublishNull((Double)null);
  }

  @Test
  public void testSendFloat() {
    testSend(13.456f);
  }

  @Test
  public void testSendNullFloat() {
    testSendNull((Float)null);
  }

  @Test
  public void testReplyFloat() {
    testReply(13.456f);
  }

  @Test
  public void testReplyNullFloat() {
    testReplyNull((Float) null);
  }

  @Test
  public void testPublishFloat() {
    testPublish(12.123f);
  }

  @Test
  public void testPublishNullFloat() {
    testPublishNull((Float)null);
  }

  @Test
  public void testSendInteger() {
    testSend(123);
  }

  @Test
  public void testSendNullInteger() {
    testSendNull((Integer)null);
  }

  @Test
  public void testReplyInteger() {
    testReply(123);
  }

  @Test
  public void testReplyNullInteger() {
    testReplyNull((Integer) null);
  }

  @Test
  public void testPublishInteger() {
    testPublish(123);
  }

  @Test
  public void testPublishNullInteger() {
    testPublishNull((Integer)null);
  }

  @Test
  public void testSendLong() {
    testSend(123l);
  }

  @Test
  public void testSendNullLong() {
    testSendNull((Long)null);
  }

  @Test
  public void testReplyLong() {
    testReply(123l);
  }

  @Test
  public void testReplyNullLong() {
    testReplyNull((Long) null);
  }

  @Test
  public void testPublishLong() {
    testPublish(123123l);
  }

  @Test
  public void testPublishNullLong() {
    testPublishNull((Long)null);
  }

  @Test
  public void testSendShort() {
    testSend((short)123);
  }

  @Test
  public void testSendNullShort() {
    testSendNull((Short)null);
  }

  @Test
  public void testReplyShort() {
    testReply((short) 123);
  }

  @Test
  public void testReplyNullShort() {
    testReplyNull((Short) null);
  }

  @Test
  public void testPublishShort() {
    testPublish((short)1234);
  }

  @Test
  public void testPublishNullShort() {
    testPublishNull((Short)null);
  }

  @Test
  public void testSendJsonArray() {
    JsonArray arr = new JsonArray();
    arr.add("hello").add(123).add(false);
    testSendWithAsserter(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendNullJsonArray() {
    testSendNull((JsonArray)null);
  }

  @Test
  public void testReplyJsonArray() {
    JsonArray arr = new JsonArray();
    arr.add("hello").add(123).add(false);
    testReplyWithAsserter(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyNullJsonArray() {
    testReplyNull((JsonArray) null);
  }

  @Test
  public void testPublishJsonArray() {
    JsonArray arr = new JsonArray();
    arr.add("hello").add(123).add(false);
    testPublishWithAsserter(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishNullJsonArray() {
    testPublishNull((JsonArray)null);
  }

  @Test
  public void testSendJsonObject() {
    JsonObject obj = new JsonObject();
    obj.putString("foo", "bar").putNumber("quux", 123);
    testSendWithAsserter(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendNullJsonObject() {
    testSendNull((JsonObject)null);
  }

  @Test
  public void testReplyJsonObject() {
    JsonObject obj = new JsonObject();
    obj.putString("foo", "bar").putNumber("quux", 123);
    testReplyWithAsserter(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyNullJsonObject() {
    testReplyNull((JsonObject) null);
  }

  @Test
  public void testPublishJsonObject() {
    JsonObject obj = new JsonObject();
    obj.putString("foo", "bar").putNumber("quux", 123);
    testPublishWithAsserter(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishNullJsonObject() {
    testPublishNull((JsonObject)null);
  }

  @Test
  public void testPublish() {
    String str = randomUnicodeString(1000);
    eb.registerHandler(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    });
    eb.publish(ADDRESS1, str);
    await();
  }

  @Test
  public void testPublishMultipleHandlers() {
    String str = randomUnicodeString(1000);
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

  private <T> void testSend(T val) {
    eb.registerHandler(ADDRESS1,msg -> {
      assertEquals(val, msg.body());
      testComplete();
    });
    eb.send(ADDRESS1, val);
    await();
  }

  private <T> void testSendWithAsserter(T val, Consumer<T> consumer) {
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      consumer.accept(msg.body());
      testComplete();
    });
    eb.send(ADDRESS1, val);
    await();
  }

  private <T> void testSendNull(T obj) {
    eb.registerHandler(ADDRESS1,msg -> {
      assertNull(msg.body());
      testComplete();
    });
    eb.send(ADDRESS1, (T)null);
    await();
  }

  private <T> void testReply(T val) {
    eb.registerHandler(ADDRESS1, msg -> {
      assertEquals("foo", msg.body());
      msg.reply(val);
    });
    eb.send(ADDRESS1, "foo", (Message<T> reply) -> {
      assertEquals(val, reply.body());
      testComplete();
    });
    await();
  }

  private <T> void testReplyWithAsserter(T val, Consumer<T> consumer) {
    eb.registerHandler(ADDRESS1, msg -> {
      assertEquals("foo", msg.body());
      msg.reply(val);
    });
    eb.send(ADDRESS1, "foo", (Message<T>reply) -> {
      consumer.accept(reply.body());
      testComplete();
    });
    await();
  }

  private <T> void testReplyNull(T val) {
    eb.registerHandler(ADDRESS1, msg -> {
      assertEquals("foo", msg.body());
      msg.reply((T)null);
    });
    eb.send(ADDRESS1, "foo", (Message<T>reply) -> {
      assertNull(reply.body());
      testComplete();
    });
    await();
  }

  private <T> void testPublish(T val) {
    AtomicInteger count = new AtomicInteger();
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      assertEquals(val, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      assertEquals(val, msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.publish(ADDRESS1, (T)val);
    await();
  }

  private <T> void testPublishNull(T val) {
    AtomicInteger count = new AtomicInteger();
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      assertNull(msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      assertNull(msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.publish(ADDRESS1, (T)null);
    await();
  }

  private <T> void testPublishWithAsserter(T val, Consumer<T> consumer) {
    AtomicInteger count = new AtomicInteger();
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      consumer.accept(msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.registerHandler(ADDRESS1, (Message<T> msg) -> {
      consumer.accept(msg.body());
      if (count.incrementAndGet() == 2) {
        testComplete();
      }
    });
    eb.publish(ADDRESS1, (T)val);
    await();
  }



}

