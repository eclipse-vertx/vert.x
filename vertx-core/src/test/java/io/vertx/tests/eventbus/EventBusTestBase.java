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

import io.netty.util.CharsetUtil;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.tests.shareddata.AsyncMapTest.SomeClusterSerializableObject;
import io.vertx.tests.shareddata.AsyncMapTest.SomeSerializableObject;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.vertx.core.eventbus.impl.CodecManager.STRING_MESSAGE_CODEC;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class EventBusTestBase extends VertxTestBase {

  protected static final String ADDRESS1 = "some-address1";
  protected static final String ADDRESS2 = "some-address2";

  protected abstract Vertx[] vertices(int num);

  @Test
  public void testSendNull() throws Exception {
    testSend(null);
  }

  @Test
  public void testReplyNull() throws Exception {
    testReply(null);
  }

  @Test
  public void testPublishNull() throws Exception {
    testPublish(null);
  }

  @Test
  public void testSendString() throws Exception {
    String str = TestUtils.randomUnicodeString(100);
    testSend(str);
  }

  @Test
  public void testReplyString() throws Exception {
    String str = TestUtils.randomUnicodeString(100);
    testReply(str);
  }

  @Test
  public void testPublishString() throws Exception {
    String str = TestUtils.randomUnicodeString(100);
    testPublish(str);
  }

  @Test
  public void testSendBooleanTrue() throws Exception {
    testSend(true);
  }

  @Test
  public void testSendBooleanFalse() throws Exception {
    testSend(false);
  }

  @Test
  public void testReplyBooleanTrue() throws Exception {
    testReply(true);
  }

  @Test
  public void testReplyBooleanFalse() throws Exception {
    testReply(false);
  }

  @Test
  public void testPublishBooleanTrue() throws Exception {
    testPublish(true);
  }

  @Test
  public void testPublishBooleanFalse() throws Exception {
    testPublish(false);
  }

  @Test
  public void testSendBuffer() throws Exception {
    Buffer sent = TestUtils.randomBuffer(100);
    testSend(sent, (buffer) -> {
      assertEquals(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testReplyBuffer() throws Exception {
    Buffer sent = TestUtils.randomBuffer(100);
    testReply(sent, (bytes) -> {
      assertEquals(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testPublishBuffer() throws Exception {
    Buffer sent = TestUtils.randomBuffer(100);
    testPublish(sent, (buffer) -> {
      assertEquals(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testSendByte() throws Exception {
    testSend(TestUtils.randomByte());
  }

  @Test
  public void testReplyByte() throws Exception {
    testReply(TestUtils.randomByte());
  }

  @Test
  public void testPublishByte() throws Exception {
    testPublish(TestUtils.randomByte());
  }

  @Test
  public void testSendByteArray() throws Exception {
    byte[] sent = TestUtils.randomByteArray(100);
    testSend(sent, (bytes) -> {
      TestUtils.byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testReplyByteArray() throws Exception {
    byte[] sent = TestUtils.randomByteArray(100);
    testReply(sent, (bytes) -> {
      TestUtils.byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testPublishByteArray() throws Exception {
    byte[] sent = TestUtils.randomByteArray(100);
    testPublish(sent, (bytes) -> {
      TestUtils.byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testSendCharacter() throws Exception {
    testSend(TestUtils.randomChar());
  }

  @Test
  public void testReplyCharacter() throws Exception {
    testReply(TestUtils.randomChar());
  }

  @Test
  public void testPublishCharacter() throws Exception {
    testPublish(TestUtils.randomChar());
  }

  @Test
  public void testSendDouble() throws Exception {
    testSend(TestUtils.randomDouble());
  }

  @Test
  public void testReplyDouble() throws Exception {
    testReply(TestUtils.randomDouble());
  }

  @Test
  public void testPublishDouble() throws Exception {
    testPublish(TestUtils.randomDouble());
  }

  @Test
  public void testSendFloat() throws Exception {
    testSend(TestUtils.randomFloat());
  }

  @Test
  public void testReplyFloat() throws Exception {
    testReply(TestUtils.randomFloat());
  }

  @Test
  public void testPublishFloat() throws Exception {
    testPublish(TestUtils.randomFloat());
  }

  @Test
  public void testSendInteger() throws Exception {
    testSend(TestUtils.randomInt());
  }

  @Test
  public void testReplyInteger() throws Exception {
    testReply(TestUtils.randomInt());
  }

  @Test
  public void testPublishInteger() throws Exception {
    testPublish(TestUtils.randomInt());
  }

  @Test
  public void testSendLong() throws Exception {
    testSend(TestUtils.randomLong());
  }

  @Test
  public void testReplyLong() throws Exception {
    testReply(TestUtils.randomLong());
  }

  @Test
  public void testPublishLong() throws Exception {
    testPublish(TestUtils.randomLong());
  }

  @Test
  public void testSendShort() throws Exception {
    testSend(TestUtils.randomShort());
  }

  @Test
  public void testReplyShort() throws Exception {
    testReply(TestUtils.randomShort());
  }

  @Test
  public void testPublishShort() throws Exception {
    testPublish(TestUtils.randomShort());
  }

  @Test
  public void testSendBigInteger() throws Exception {
    testSend(BigInteger.valueOf(TestUtils.randomLong()));
  }

  @Test
  public void testReplyBigInteger() throws Exception {
    testReply(BigInteger.valueOf(TestUtils.randomLong()));
  }

  @Test
  public void testPublishBigInteger() throws Exception {
    testPublish(BigInteger.valueOf(TestUtils.randomLong()));
  }

  @Test
  public void testSendBigDecimal() throws Exception {
    testSend(BigDecimal.valueOf(TestUtils.randomDouble()));
  }

  @Test
  public void testReplyBigDecimal() throws Exception {
    testReply(BigDecimal.valueOf(TestUtils.randomDouble()));
  }

  @Test
  public void testPublishBigDecimal() throws Exception {
    testPublish(BigDecimal.valueOf(TestUtils.randomDouble()));
  }

  @Test
  public void testSendJsonArray() throws Exception {
    JsonArray arr = new JsonArray();
    arr.add(TestUtils.randomUnicodeString(100)).add(TestUtils.randomInt()).add(TestUtils.randomBoolean());
    testSend(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyJsonArray() throws Exception {
    JsonArray arr = new JsonArray();
    arr.add(TestUtils.randomUnicodeString(100)).add(TestUtils.randomInt()).add(TestUtils.randomBoolean());
    testReply(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishJsonArray() throws Exception {
    JsonArray arr = new JsonArray();
    arr.add(TestUtils.randomUnicodeString(100)).add(TestUtils.randomInt()).add(TestUtils.randomBoolean());
    testPublish(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendJsonObject() throws Exception {
    JsonObject obj = new JsonObject();
    obj.put(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).put(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testSend(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyJsonObject() throws Exception {
    JsonObject obj = new JsonObject();
    obj.put(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).put(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testReply(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishJsonObject() throws Exception {
    JsonObject obj = new JsonObject();
    obj.put(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).put(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testPublish(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendClusterSerializable() throws Exception {
    SomeClusterSerializableObject obj = new SomeClusterSerializableObject(TestUtils.randomAlphaString(50));
    testSend(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyClusterSerializable() throws Exception {
    SomeClusterSerializableObject obj = new SomeClusterSerializableObject(TestUtils.randomAlphaString(50));
    testReply(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishClusterSerializable() throws Exception {
    SomeClusterSerializableObject obj = new SomeClusterSerializableObject(TestUtils.randomAlphaString(50));
    testPublish(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendSerializable() throws Exception {
    SomeSerializableObject obj = new SomeSerializableObject(TestUtils.randomAlphaString(50));
    testSend(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplySerializable() throws Exception {
    SomeSerializableObject obj = new SomeSerializableObject(TestUtils.randomAlphaString(50));
    testReply(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishSerializable() throws Exception {
    SomeSerializableObject obj = new SomeSerializableObject(TestUtils.randomAlphaString(50));
    testPublish(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendWithCodecFromSelector() throws Exception {
    ImmutableObject obj = new ImmutableObject(TestUtils.randomAlphaString(15));
    testSend(obj, (received) -> {
      assertEquals(obj, received);
      assertEquals(shouldImmutableObjectBeCopied(), obj != received);
    });
  }

  @Test
  public void testReplyWithCodecFromSelector() throws Exception {
    ImmutableObject obj = new ImmutableObject(TestUtils.randomAlphaString(15));
    testReply(obj, (received) -> {
      assertEquals(obj, received);
      assertEquals(shouldImmutableObjectBeCopied(), obj != received);
    });
  }

  @Test
  public void testPublishWithCodecFromSelector() throws Exception {
    ImmutableObject obj = new ImmutableObject(TestUtils.randomAlphaString(15));
    testPublish(obj, (received) -> {
      assertEquals(obj, received);
      assertEquals(shouldImmutableObjectBeCopied(), obj != received);
    });
  }

  protected abstract boolean shouldImmutableObjectBeCopied();

  @Test
  public void testSendWithHeaders() throws Exception {
    testSend("foo", "foo", null, new DeliveryOptions().addHeader("uhqwduh", "qijwdqiuwd").addHeader("iojdijef", "iqjwddh"));
  }

  @Test
  public void testSendWithDeliveryOptionsButNoHeaders() throws Exception {
    testSend("foo", "foo", null, new DeliveryOptions());
  }

  @Test
  public void testReplyWithHeaders() throws Exception {
    testReply("foo", "foo", null, new DeliveryOptions().addHeader("uhqwduh", "qijwdqiuwd").addHeader("iojdijef", "iqjwddh"));
  }

  @Test
  public void testReplyFromWorker() throws Exception {
    String expectedBody = TestUtils.randomAlphaString(20);
    Vertx[] vertices = vertices(2);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[1].deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        vertx.eventBus().<String>consumer(ADDRESS1, msg -> {
          msg.reply(expectedBody);
        }).completion().onComplete(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    awaitLatch(latch);
    vertices[0].eventBus().request(ADDRESS1, "whatever").onComplete(onSuccess(reply -> {
      assertEquals(expectedBody, reply.body());
      testComplete();
    }));
    await();
  }

  @Test
  public void testSendFromExecuteBlocking() throws Exception {
    String expectedBody = TestUtils.randomAlphaString(20);
    CountDownLatch receivedLatch = new CountDownLatch(1);
    Vertx[] vertices = vertices(2);
    vertices[1].eventBus().<String>consumer(ADDRESS1, msg -> {
      assertEquals(expectedBody, msg.body());
      receivedLatch.countDown();
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      vertices[0].executeBlocking(() -> {
        vertices[0].eventBus().send(ADDRESS1, expectedBody);
        try {
          awaitLatch(receivedLatch); // Make sure message is sent even if we're busy
        } catch (InterruptedException e) {
          Thread.interrupted();
          throw e;
        }
        return null;
      }).onComplete(onSuccess(ar2 -> testComplete()));
    });
    await();
  }

  @Test
  public void testNoHandlersCallbackContext() {
    Vertx[] vertices = vertices(2);
    waitFor(4);

    // On an "external" thread
    vertices[0].eventBus().request("blah", "blah").onComplete(onFailure(err -> {
      if (err instanceof ReplyException) {
        ReplyException cause = (ReplyException) err;
        assertSame(ReplyFailure.NO_HANDLERS, cause.failureType());
      } else {
        fail(err);
      }
      assertTrue("Not an EL thread", Context.isOnEventLoopThread());
      complete();
    }));

    // On a EL context
    vertices[0].runOnContext(v -> {
      Context ctx = vertices[0].getOrCreateContext();
      vertices[0].eventBus().request("blah", "blah").onComplete(onFailure(err -> {
        if (err instanceof ReplyException) {
          ReplyException cause = (ReplyException) err;
          assertSame(ReplyFailure.NO_HANDLERS, cause.failureType());
        } else {
          fail(err);
        }
        assertSame(ctx, vertices[0].getOrCreateContext());
        complete();
      }));
    });

    // On a Worker context
    vertices[0].deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        Context ctx = getVertx().getOrCreateContext();
        vertices[0].eventBus().request("blah", "blah").onComplete(onFailure(err -> {
          if (err instanceof ReplyException) {
            ReplyException cause = (ReplyException) err;
            assertSame(ReplyFailure.NO_HANDLERS, cause.failureType());
          } else {
            fail(err);
          }
          assertSame(ctx, getVertx().getOrCreateContext());
          complete();
        }));
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).await();

    // Inside executeBlocking
    vertices[0].executeBlocking(() -> {
      vertices[0].eventBus().request("blah", "blah").onComplete(onFailure(err -> {
        if (err instanceof ReplyException) {
          ReplyException cause = (ReplyException) err;
          assertSame(ReplyFailure.NO_HANDLERS, cause.failureType());
        } else {
          fail(err);
        }
        assertTrue("Not an EL thread", Context.isOnEventLoopThread());
        complete();
      }));
      return null;
    }, false);

    await();
  }

  protected <T> void testSend(T val) throws Exception {
    testSend(val, null);
  }

  protected <T, R> void testSend(T val, R received, Consumer<T> consumer, DeliveryOptions options) throws Exception {
    Vertx[] vertices = vertices(2);
    MessageConsumer<T> reg = vertices[1].eventBus().<T>consumer(ADDRESS1).handler((Message<T> msg) -> {
      if (consumer == null) {
        assertTrue(msg.isSend());
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
    awaitFuture(reg.completion());
    if (options == null) {
      vertices[0].eventBus().send(ADDRESS1, val);
    } else {
      vertices[0].eventBus().send(ADDRESS1, val, options);
    }
    await();
  }

  protected <T> void testSend(T val, Consumer<T> consumer) throws Exception {
    testSend(val, val, consumer, null);
  }

  protected <T> void testReply(T val) throws Exception {
    testReply(val, null);
  }

  protected <T> void testReply(T val, Consumer<T> consumer) throws Exception {
    testReply(val, val, consumer, null);
  }

  protected <T, R> void testReply(T val, R received, Consumer<R> consumer, DeliveryOptions options) throws Exception {
    Vertx[] vertices = vertices(2);
    String str = TestUtils.randomUnicodeString(1000);
    MessageConsumer<?> reg = vertices[1].eventBus().consumer(ADDRESS1, msg -> {
      assertEquals(str, msg.body());
      if (options == null) {
        msg.reply(val);
      } else {
        msg.reply(val, options);
      }
    });
    awaitFuture(reg.completion());
    vertices[0].eventBus().<R>request(ADDRESS1, str).onComplete(onSuccess((Message<R> reply) -> {
      if (consumer == null) {
        assertTrue(reply.isSend());
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
    await();
  }

  protected <T> void testPublish(T val) throws Exception {
    testPublish(val, null);
  }

  protected <T> void testPublish(T val, Consumer<T> consumer) throws Exception {
    Vertx[] vertices = vertices(3);
    waitFor(vertices.length - 1);
    for (int i = 1;i < vertices.length;i++) {
      MessageConsumer<?> reg = vertices[i].eventBus().<T>consumer(ADDRESS1).handler(msg -> {
        if (consumer == null) {
          assertFalse(msg.isSend());
          assertEquals(val, msg.body());
        } else {
          consumer.accept(msg.body());
        }
        complete();
      });
      awaitFuture(reg.completion());
    }
    vertices[0].eventBus().publish(ADDRESS1, val);
    await();
  }

  public static class MySystemDecoder implements MessageCodec<MyPOJO, String> {

    @Override
    public void encodeToWire(Buffer buffer, MyPOJO s) {
    }

    @Override
    public String decodeFromWire(int pos, Buffer buffer) {
      return null;
    }

    @Override
    public String transform(MyPOJO s) {
      return null;
    }

    @Override
    public String name() {
      return "mysystemdecoder";
    }

    @Override
    public byte systemCodecID() {
      return 0;
    }
  }

  public static class NullNameCodec implements MessageCodec<String, String> {

    @Override
    public void encodeToWire(Buffer buffer, String s) {

    }

    @Override
    public String decodeFromWire(int pos, Buffer buffer) {
      return null;
    }

    @Override
    public String transform(String s) {
      return null;
    }

    @Override
    public String name() {
      return null;
    }

    @Override
    public byte systemCodecID() {
      return 0;
    }
  }

  public static class MyPOJOEncoder1 implements MessageCodec<MyPOJO, String> {

    @Override
    public void encodeToWire(Buffer buffer, MyPOJO myPOJO) {
      byte[] bytes = myPOJO.getStr().getBytes(CharsetUtil.UTF_8);
      buffer.appendInt(bytes.length);
      buffer.appendBytes(bytes);
    }

    @Override
    public String decodeFromWire(int pos, Buffer buffer) {
      int length = buffer.getInt(pos);
      pos += 4;
      byte[] bytes = buffer.getBytes(pos, pos + length);
      return new String(bytes, CharsetUtil.UTF_8);
    }

    @Override
    public String transform(MyPOJO myPOJO) {
      return myPOJO.getStr();
    }

    @Override
    public String name() {
      return "mypojoencoder1";
    }

    @Override
    public byte systemCodecID() {
      return -1;
    }
  }

  public static class MyPOJOEncoder2 implements MessageCodec<MyPOJO, MyPOJO> {

    @Override
    public void encodeToWire(Buffer buffer, MyPOJO myPOJO) {
      byte[] bytes = myPOJO.getStr().getBytes(CharsetUtil.UTF_8);
      buffer.appendInt(bytes.length);
      buffer.appendBytes(bytes);
    }

    @Override
    public MyPOJO decodeFromWire(int pos, Buffer buffer) {
      int length = buffer.getInt(pos);
      pos += 4;
      byte[] bytes = buffer.getBytes(pos, pos + length);
      String str = new String(bytes, CharsetUtil.UTF_8);
      return new MyPOJO(str);
    }

    @Override
    public MyPOJO transform(MyPOJO myPOJO) {
      return new MyPOJO(myPOJO.getStr());
    }

    @Override
    public String name() {
      return "mypojoencoder2";
    }

    @Override
    public byte systemCodecID() {
      return -1;
    }
  }


  public static class MyPOJO {
    private String str;

    public MyPOJO(String str) {
      this.str = str;
    }

    public String getStr() {
      return str;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyPOJO myPOJO = (MyPOJO) o;
      if (str != null ? !str.equals(myPOJO.str) : myPOJO.str != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return str != null ? str.hashCode() : 0;
    }
  }

  public static class MyReplyException extends ReplyException {

    public MyReplyException(int failureCode, String message) {
      super(ReplyFailure.RECIPIENT_FAILURE, failureCode, message);
    }
  }

  public static class MyReplyExceptionMessageCodec implements
      MessageCodec<MyReplyException, MyReplyException> {

    @Override
    public void encodeToWire(Buffer buffer, MyReplyException body) {
      buffer.appendInt(body.failureCode());
      if (body.getMessage() == null) {
        buffer.appendByte((byte)0);
      } else {
        buffer.appendByte((byte)1);
        byte[] encoded = body.getMessage().getBytes(CharsetUtil.UTF_8);
        buffer.appendInt(encoded.length);
        buffer.appendBytes(encoded);
      }
    }

    @Override
    public MyReplyException decodeFromWire(int pos, Buffer buffer) {
      int failureCode = buffer.getInt(pos);
      pos += 4;
      boolean isNull = buffer.getByte(pos) == (byte)0;
      String message;
      if (!isNull) {
        pos++;
        int strLength = buffer.getInt(pos);
        pos += 4;
        byte[] bytes = buffer.getBytes(pos, pos + strLength);
        message = new String(bytes, CharsetUtil.UTF_8);
      } else {
        message = null;
      }
      return new MyReplyException(failureCode, message);
    }

    @Override
    public MyReplyException transform(MyReplyException obj) {
      return obj;
    }

    @Override
    public String name() {
      return "myReplyException";
    }

    @Override
    public byte systemCodecID() {
      return -1;
    }
  }

  public static class StringLengthCodec implements MessageCodec<String, Integer> {

    @Override
    public void encodeToWire(Buffer buffer, String s) {
      buffer.appendInt(s.length());
    }

    @Override
    public Integer decodeFromWire(int pos, Buffer buffer) {
      return buffer.getInt(pos);
    }

    @Override
    public Integer transform(String s) {
      return s.length();
    }

    @Override
    public String name() {
      return getClass().getName();
    }

    @Override
    public byte systemCodecID() {
      return -1;
    }
  }

  public static class ImmutableObject {
    public final String str;

    public ImmutableObject(String str) {
      this.str = Objects.requireNonNull(str);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ImmutableObject that = (ImmutableObject) o;
      return str.equals(that.str);
    }

    @Override
    public int hashCode() {
      return str.hashCode();
    }
  }

  public static class ImmutableObjectCodec implements MessageCodec<ImmutableObject, ImmutableObject> {

    @Override
    public void encodeToWire(Buffer buffer, ImmutableObject immutableObject) {
      STRING_MESSAGE_CODEC.encodeToWire(buffer, immutableObject.str);
    }

    @Override
    public ImmutableObject decodeFromWire(int pos, Buffer buffer) {
      return new ImmutableObject(STRING_MESSAGE_CODEC.decodeFromWire(pos, buffer));
    }

    @Override
    public ImmutableObject transform(ImmutableObject immutableObject) {
      return immutableObject;
    }

    @Override
    public String name() {
      return "ImmutableObjectCodec";
    }

    @Override
    public byte systemCodecID() {
      return -1;
    }
  }

  @Test
  public void testConsumerUnregistrationContextCallback() throws Exception {
    Vertx[] vertices = vertices(1);
    Vertx vertx = vertices[0];
    CompletableFuture<MessageConsumer<?>> latch = new CompletableFuture<>();
    Thread th = new Thread(() -> {
      Context ctx = vertx.getOrCreateContext();
      ctx.runOnContext(v1 -> {
        MessageConsumer<?> consumer = vertx.eventBus().consumer(ADDRESS1);
        latch.complete(consumer);
      });
    });
    th.start();
    MessageConsumer<?> consumer = latch.get(20, TimeUnit.SECONDS);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      consumer.unregister().onComplete(onSuccess(v2 -> {
        assertSame(ctx, Vertx.currentContext());
        testComplete();
      }));
    });
    await();
  }
}
