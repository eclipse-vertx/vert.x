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

package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.time.Instant;
import java.util.*;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonMapperTest extends VertxTestBase {

  @Test
  public void encodeCustomTypeInstant() {
    Instant now = Instant.now();
    String json = Json.encodeValue(now);
    assertNotNull(json);
    // the RFC is one way only
    Instant decoded = Instant.from(ISO_INSTANT.parse(json.substring(1, json.length() - 1)));
    assertEquals(now, decoded);
  }

  @Test
  public void encodeCustomTypeInstantNull() {
    Instant now = null;
    String json = Json.encodeValue(now);
    assertNotNull(json);
    assertEquals("null", json);
  }

  @Test
  public void encodeCustomTypeBinary() {
    byte[] data = new byte[] { 'h', 'e', 'l', 'l', 'o'};
    String json = Json.encodeValue(data);
    assertNotNull(json);
    // base64 encoded hello
    assertEquals("\"aGVsbG8=\"", json);
  }

  @Test
  public void encodeCustomTypeBinaryNull() {
    byte[] data = null;
    String json = Json.encodeValue(data);
    assertNotNull(json);
    assertEquals("null", json);
  }

  @Test
  public void encodeToBuffer() {
    Buffer json = Json.encodeToBuffer("Hello World!");
    assertNotNull(json);
    // json strings are always UTF8
    assertEquals("\"Hello World!\"", json.toString("UTF-8"));
  }

  @Test
  public void testPOJODecodingWithCodecs() {
    Pojo original = new Pojo();
    original.value = "test";

    assertEquals(original, Json.decodeValue("{\"value\":\"test\"}", Pojo.class));
  }

  @Test
  public void testPOJOEncodingWithCodecs() {
    Pojo original = new Pojo();
    original.value = "test";

    assertEquals("{\"value\":\"test\"}", Json.encodeValue(original));
  }

  @Test
  public void testInstantDecoding() {
    Pojo original = new Pojo();
    original.instant = Instant.from(ISO_INSTANT.parse("2018-06-20T07:25:38.397Z"));
    Pojo decoded = Json.decodeValue("{\"instant\":\"2018-06-20T07:25:38.397Z\"}", Pojo.class);
    assertEquals(original.instant, decoded.instant);
  }

  @Test
  public void testNullInstantDecoding() {
    Pojo original = new Pojo();
    Pojo decoded = Json.decodeValue("{\"instant\":null}", Pojo.class);
    assertEquals(original.instant, decoded.instant);
  }

  @Test
  public void testBytesDecoding() {
    Pojo original = new Pojo();
    original.bytes = TestUtils.randomByteArray(12);
    Pojo decoded = Json.decodeValue("{\"bytes\":\"" + Base64.getEncoder().encodeToString(original.bytes) + "\"}", Pojo.class);
    assertArrayEquals(original.bytes, decoded.bytes);
  }

  @Test
  public void testNullBytesDecoding() {
    Pojo original = new Pojo();
    Pojo decoded = Json.decodeValue("{\"bytes\":null}", Pojo.class);
    assertEquals(original.bytes, decoded.bytes);
  }

  @Test
  public void testDecodeBufferUnknowContent() {
    testDecodeUnknowContent(true);
  }

  @Test
  public void testDecodeStringUnknowContent() {
    testDecodeUnknowContent(false);
  }

  private void testDecodeUnknowContent(boolean asBuffer) {
    String number = String.valueOf(1);
    assertEquals(1, asBuffer ? Json.decode(Buffer.buffer(number)) : Json.decode(number));

    String bool = Boolean.TRUE.toString();
    assertEquals(true, asBuffer ? Json.decode(Buffer.buffer(bool)) : Json.decode(bool));

    String text = "\"whatever\"";
    assertEquals("whatever", asBuffer ? Json.decode(Buffer.buffer(text)) : Json.decode(text));

    String nullText = "null";
    assertNull(asBuffer ? Json.decode(Buffer.buffer(nullText)) : Json.decode(nullText));

    JsonObject obj = new JsonObject().put("foo", "bar");
    assertEquals(obj, asBuffer ? Json.decode(obj.toBuffer()) : Json.decode(obj.toString()));

    JsonArray arr = new JsonArray().add(1).add(false).add("whatever").add(obj);
    assertEquals(arr, asBuffer ? Json.decode(arr.toBuffer()) : Json.decode(arr.toString()));

    String invalidText = "\"invalid";
    try {
      if (asBuffer) {
        Json.decode(Buffer.buffer(invalidText));
      } else {
        Json.decode(invalidText);
      }
      fail();
    } catch (DecodeException ignore) {
    }
  }

  @Test
  public void testEncode() {
    Integer i = 4;
    assertEquals(Integer.toString(i), Json.encode(i));

    Long l = 4L;
    assertEquals(Long.toString(l), Json.encode(l));

    Float f = 3.4f;
    assertEquals(Float.toString(f), Json.encode(f));

    Double d = 5.2;
    assertEquals(Double.toString(d), Json.encode(d));

    String s = "aaa";
    assertEquals("\"aaa\"", Json.encode(s));

    Object n = null;
    assertEquals("null", Json.encode(n));

    Boolean b = true;
    assertEquals(Boolean.toString(b), Json.encode(b));

    JsonArray array = new JsonArray()
      .add(5)
      .add(10L)
      .add(3.4f)
      .add(5.2)
      .add("aaa")
      .addNull()
      .add(true)
      .add(new JsonArray().add("bbb"))
      .add(new JsonObject().put("ccc", "ddd"));

    assertEquals("[5,10,3.4,5.2,\"aaa\",null,true,[\"bbb\"],{\"ccc\":\"ddd\"}]", array.encode());

    JsonObject object = new JsonObject()
      .put("i", 5)
      .put("l", 10L)
      .put("f", 3.4f)
      .put("d", 5.2d)
      .put("s", "aaa")
      .putNull("n")
      .put("b", true)
      .put("array", new JsonArray().add("bbb"))
      .put("object", new JsonObject().put("ccc", "ddd"));

    assertEquals("{\"i\":5,\"l\":10,\"f\":3.4,\"d\":5.2,\"s\":\"aaa\",\"n\":null,\"b\":true,\"array\":[\"bbb\"],\"object\":{\"ccc\":\"ddd\"}}", object.encode());
  }

  @Test
  public void testEncodePrettily() {
    JsonObject object = new JsonObject()
      .put("hello", "world");

    assertEquals(
      "{\n" +
      "  \"hello\" : \"world\"\n" +
      "}", object.encodePrettily());
  }
}
