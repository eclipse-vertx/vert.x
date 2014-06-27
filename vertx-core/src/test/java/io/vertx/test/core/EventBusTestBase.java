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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class EventBusTestBase extends AsyncTestBase {

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
      TestUtils.buffersEqual(sent, buffer);
      assertFalse(sent == buffer); // Make sure it's copied
    });
  }

  @Test
  public void testReplyBuffer() {
    Buffer sent = TestUtils.randomBuffer(100);
    testReply(sent, (bytes) -> {
      TestUtils.buffersEqual(sent, bytes);
      assertFalse(sent == bytes); // Make sure it's copied
    });
  }

  @Test
  public void testPublishBuffer() {
    Buffer sent = TestUtils.randomBuffer(100);
    testPublish(sent, (buffer) -> {
      TestUtils.buffersEqual(sent, buffer);
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
    obj.putString(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).putNumber(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testSend(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testReplyJsonObject() {
    JsonObject obj = new JsonObject();
    obj.putString(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).putNumber(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testReply(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  @Test
  public void testPublishJsonObject() {
    JsonObject obj = new JsonObject();
    obj.putString(TestUtils.randomUnicodeString(100), TestUtils.randomUnicodeString(100)).putNumber(TestUtils.randomUnicodeString(100), TestUtils.randomInt());
    testPublish(obj, (received) -> {
      assertEquals(obj, received);
      assertFalse(obj == received); // Make sure it's copied
    });
  }

  protected <T> void testSend(T val) {
    testSend(val, null);
  }

  protected abstract <T> void testSend(T val, Consumer<T> consumer);

  protected <T> void testReply(T val) {
    testReply(val, null);
  }

  protected abstract <T> void testReply(T val, Consumer<T> consumer);

  protected <T> void testPublish(T val) {
    testPublish(val, null);
  }

  protected abstract <T> void testPublish(T val, Consumer<T> consumer);
}
