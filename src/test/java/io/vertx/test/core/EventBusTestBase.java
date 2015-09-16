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

import io.netty.util.CharsetUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class EventBusTestBase extends VertxTestBase {

  protected static final String ADDRESS1 = "some-address1";
  protected static final String ADDRESS2 = "some-address2";

  @Test
  public void testSendNull() {
    testSend(null);
  }

  @Test
  public void testReplyNull() {
    testReply(null);
  }

  @Test
  public void testPublishNull() {
    testPublish(null);
  }

  @Test
  public void testSendString() {
    String str = TestUtils.randomUnicodeString(100);
    testSend(str);
  }

  @Test
  public void testReplyString() {
    String str = TestUtils.randomUnicodeString(100);
    testReply(str);
  }

  @Test
  public void testPublishString() {
    String str = TestUtils.randomUnicodeString(100);
    testPublish(str);
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
  public void testReplyBooleanTrue() {
    testReply(true);
  }

  @Test
  public void testReplyBooleanFalse() {
    testReply(false);
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
  public void testSendBuffer() {
    Buffer sent = TestUtils.randomBuffer(100);
    testSend(sent, (buffer) -> {
      assertEquals(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testReplyBuffer() {
    Buffer sent = TestUtils.randomBuffer(100);
    testReply(sent, (bytes) -> {
      assertEquals(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testPublishBuffer() {
    Buffer sent = TestUtils.randomBuffer(100);
    testPublish(sent, (buffer) -> {
      assertEquals(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testSendByte() {
    testSend(TestUtils.randomByte());
  }

  @Test
  public void testReplyByte() {
    testReply(TestUtils.randomByte());
  }

  @Test
  public void testPublishByte() {
    testPublish(TestUtils.randomByte());
  }

  @Test
  public void testSendByteArray() {
    byte[] sent = TestUtils.randomByteArray(100);
    testSend(sent, (bytes) -> {
      TestUtils.byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testReplyByteArray() {
    byte[] sent = TestUtils.randomByteArray(100);
    testReply(sent, (bytes) -> {
      TestUtils.byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testPublishByteArray() {
    byte[] sent = TestUtils.randomByteArray(100);
    testPublish(sent, (bytes) -> {
      TestUtils.byteArraysEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testSendCharacter() {
    testSend(TestUtils.randomChar());
  }

  @Test
  public void testReplyCharacter() {
    testReply(TestUtils.randomChar());
  }

  @Test
  public void testPublishCharacter() {
    testPublish(TestUtils.randomChar());
  }

  @Test
  public void testSendDouble() {
    testSend(TestUtils.randomDouble());
  }

  @Test
  public void testReplyDouble() {
    testReply(TestUtils.randomDouble());
  }

  @Test
  public void testPublishDouble() {
    testPublish(TestUtils.randomDouble());
  }

  @Test
  public void testSendFloat() {
    testSend(TestUtils.randomFloat());
  }

  @Test
  public void testReplyFloat() {
    testReply(TestUtils.randomFloat());
  }

  @Test
  public void testPublishFloat() {
    testPublish(TestUtils.randomFloat());
  }

  @Test
  public void testSendInteger() {
    testSend(TestUtils.randomInt());
  }

  @Test
  public void testReplyInteger() {
    testReply(TestUtils.randomInt());
  }

  @Test
  public void testPublishInteger() {
    testPublish(TestUtils.randomInt());
  }

  @Test
  public void testSendLong() {
    testSend(TestUtils.randomLong());
  }

  @Test
  public void testReplyLong() {
    testReply(TestUtils.randomLong());
  }

  @Test
  public void testPublishLong() {
    testPublish(TestUtils.randomLong());
  }

  @Test
  public void testSendShort() {
    testSend(TestUtils.randomShort());
  }

  @Test
  public void testReplyShort() {
    testReply(TestUtils.randomShort());
  }

  @Test
  public void testPublishShort() {
    testPublish(TestUtils.randomShort());
  }

  @Test
  public void testSendJsonArray() {
    JsonArray arr = new JsonArray();
    arr.add(TestUtils.randomUnicodeString(100)).add(TestUtils.randomInt()).add(TestUtils.randomBoolean());
    testSend(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyJsonArray() {
    JsonArray arr = new JsonArray();
    arr.add(TestUtils.randomUnicodeString(100)).add(TestUtils.randomInt()).add(TestUtils.randomBoolean());
    testReply(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishJsonArray() {
    JsonArray arr = new JsonArray();
    arr.add(TestUtils.randomUnicodeString(100)).add(TestUtils.randomInt()).add(TestUtils.randomBoolean());
    testPublish(arr, (received) -> {
      assertEquals(arr, received);
      assertFalse(arr == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendJsonObject() {
    JsonObject obj = new JsonObject();
    obj.put(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).put(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testSend(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyJsonObject() {
    JsonObject obj = new JsonObject();
    obj.put(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).put(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testReply(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishJsonObject() {
    JsonObject obj = new JsonObject();
    obj.put(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).put(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testPublish(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testSendWithHeaders() {
    testSend("foo", "foo", null, new DeliveryOptions().addHeader("uhqwduh", "qijwdqiuwd").addHeader("iojdijef", "iqjwddh"));
  }

  @Test
  public void testSendWithDeliveryOptionsButNoHeaders() {
    testSend("foo", "foo", null, new DeliveryOptions());
  }

  @Test
  public void testReplyWithHeaders() {
    testReply("foo", "foo", null, new DeliveryOptions().addHeader("uhqwduh", "qijwdqiuwd").addHeader("iojdijef", "iqjwddh"));
  }

  @Test
  public void testSendFromWorker() throws Exception {
    String expectedBody = TestUtils.randomAlphaString(20);
    startNodes(2);
    vertices[1].eventBus().<String>consumer(ADDRESS1, msg -> {
      assertEquals(expectedBody, msg.body());
      testComplete();
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[0].deployVerticle(new AbstractVerticle() {
        @Override
        public void start() throws Exception {
          vertices[0].eventBus().send(ADDRESS1, expectedBody);
        }
      }, new DeploymentOptions().setWorker(true));
    });
    await();
  }

  @Test
  public void testReplyFromWorker() throws Exception {
    String expectedBody = TestUtils.randomAlphaString(20);
    startNodes(2);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[0].deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertices[1].eventBus().<String>consumer(ADDRESS1, msg -> {
          msg.reply(expectedBody);
        }).completionHandler(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
      }
    }, new DeploymentOptions().setWorker(true));
    awaitLatch(latch);
    vertices[0].eventBus().send(ADDRESS1, "whatever", reply -> {
      assertTrue(reply.succeeded());
      assertEquals(expectedBody, reply.result().body());
      testComplete();
    });
    await();
  }

  protected <T> void testSend(T val) {
    testSend(val, null);
  }

  protected abstract <T, R> void testSend(T val, R received, Consumer<T> consumer, DeliveryOptions options);

  protected abstract <T> void testSend(T val, Consumer<T> consumer);

  protected <T> void testReply(T val) {
    testReply(val, null);
  }

  protected abstract <T> void testReply(T val, Consumer<T> consumer);

  protected abstract <T, R> void testReply(T val, R received, Consumer<R> consumer, DeliveryOptions options);

  protected <T> void testPublish(T val) {
    testPublish(val, null);
  }

  protected abstract <T> void testPublish(T val, Consumer<T> consumer);

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

}
