/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Utils;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonCodecTest {

  private static final TypeReference<Integer> INTEGER_TYPE_REF = new TypeReference<Integer>() {};
  private static final TypeReference<Long> LONG_TYPE_REF = new TypeReference<Long>() {};
  private static final TypeReference<String> STRING_TYPE_REF = new TypeReference<String>() {};
  private static final TypeReference<Float> FLOAT_TYPE_REF = new TypeReference<Float>() {};
  private static final TypeReference<Double> DOUBLE_TYPE_REF = new TypeReference<Double>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {};
  private static final TypeReference<List<Object>> LIST_TYPE_REF = new TypeReference<List<Object>>() {};
  private static final TypeReference<Boolean> BOOLEAN_TYPE_REF = new TypeReference<Boolean>() {};

  @Test
  public void testEncodeJsonObject() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("mystr", "foo");
    jsonObject.put("mycharsequence", new StringBuilder("oob"));
    jsonObject.put("myint", 123);
    jsonObject.put("mylong", 1234l);
    jsonObject.put("myfloat", 1.23f);
    jsonObject.put("mydouble", 2.34d);
    jsonObject.put("myboolean", true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonObject.put("mybinary", bytes);
    Instant now = Instant.now();
    jsonObject.put("myinstant", now);
    jsonObject.putNull("mynull");
    jsonObject.put("myobj", new JsonObject().put("foo", "bar"));
    jsonObject.put("myarr", new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String expected = "{\"mystr\":\"foo\",\"mycharsequence\":\"oob\",\"myint\":123,\"mylong\":1234,\"myfloat\":1.23,\"mydouble\":2.34,\"" +
      "myboolean\":true,\"mybinary\":\"" + strBytes + "\",\"myinstant\":\"" + ISO_INSTANT.format(now) + "\",\"mynull\":null,\"myobj\":{\"foo\":\"bar\"},\"myarr\":[\"foo\",123]}";
    String json = jsonObject.encode();
    assertEquals(expected, json);
  }

  @Test
  public void testEncodeJsonArray() {
    JsonArray jsonArray = new JsonArray();
    jsonArray.add("foo");
    jsonArray.add(123);
    jsonArray.add(1234L);
    jsonArray.add(1.23f);
    jsonArray.add(2.34d);
    jsonArray.add(true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String expected = "[\"foo\",123,1234,1.23,2.34,true,\"" + strBytes + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]";
    String json = jsonArray.encode();
    assertEquals(expected, json);
  }

  @Test
  public void testEncodeJsonObjectToBuffer() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("mystr", "foo");
    jsonObject.put("mycharsequence", new StringBuilder("oob"));
    jsonObject.put("myint", 123);
    jsonObject.put("mylong", 1234l);
    jsonObject.put("myfloat", 1.23f);
    jsonObject.put("mydouble", 2.34d);
    jsonObject.put("myboolean", true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonObject.put("mybinary", bytes);
    Instant now = Instant.now();
    jsonObject.put("myinstant", now);
    jsonObject.putNull("mynull");
    jsonObject.put("myobj", new JsonObject().put("foo", "bar"));
    jsonObject.put("myarr", new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);

    Buffer expected = Buffer.buffer("{\"mystr\":\"foo\",\"mycharsequence\":\"oob\",\"myint\":123,\"mylong\":1234,\"myfloat\":1.23,\"mydouble\":2.34,\"" +
      "myboolean\":true,\"mybinary\":\"" + strBytes + "\",\"myinstant\":\"" + ISO_INSTANT.format(now) + "\",\"mynull\":null,\"myobj\":{\"foo\":\"bar\"},\"myarr\":[\"foo\",123]}", "UTF-8");

    Buffer json = jsonObject.toBuffer();
    assertArrayEquals(expected.getBytes(), json.getBytes());
  }

  @Test
  public void testEncodeJsonArrayToBuffer() {
    JsonArray jsonArray = new JsonArray();
    jsonArray.add("foo");
    jsonArray.add(123);
    jsonArray.add(1234l);
    jsonArray.add(1.23f);
    jsonArray.add(2.34d);
    jsonArray.add(true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    Buffer expected = Buffer.buffer("[\"foo\",123,1234,1.23,2.34,true,\"" + strBytes + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]", "UTF-8");
    Buffer json = jsonArray.toBuffer();
    assertArrayEquals(expected.getBytes(), json.getBytes());
  }


  @Test
  public void testEncodeJsonObjectPrettily() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("mystr", "foo");
    jsonObject.put("myint", 123);
    jsonObject.put("mylong", 1234l);
    jsonObject.put("myfloat", 1.23f);
    jsonObject.put("mydouble", 2.34d);
    jsonObject.put("myboolean", true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonObject.put("mybinary", bytes);
    Instant now = Instant.now();
    jsonObject.put("myinstant", now);
    jsonObject.put("myobj", new JsonObject().put("foo", "bar"));
    jsonObject.put("myarr", new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String strInstant = ISO_INSTANT.format(now);
    String expected = "{" + Utils.LINE_SEPARATOR +
      "  \"mystr\" : \"foo\"," + Utils.LINE_SEPARATOR +
      "  \"myint\" : 123," + Utils.LINE_SEPARATOR +
      "  \"mylong\" : 1234," + Utils.LINE_SEPARATOR +
      "  \"myfloat\" : 1.23," + Utils.LINE_SEPARATOR +
      "  \"mydouble\" : 2.34," + Utils.LINE_SEPARATOR +
      "  \"myboolean\" : true," + Utils.LINE_SEPARATOR +
      "  \"mybinary\" : \"" + strBytes + "\"," + Utils.LINE_SEPARATOR +
      "  \"myinstant\" : \"" + strInstant + "\"," + Utils.LINE_SEPARATOR +
      "  \"myobj\" : {" + Utils.LINE_SEPARATOR +
      "    \"foo\" : \"bar\"" + Utils.LINE_SEPARATOR +
      "  }," + Utils.LINE_SEPARATOR +
      "  \"myarr\" : [ \"foo\", 123 ]" + Utils.LINE_SEPARATOR +
      "}";
    String json = jsonObject.encodePrettily();
    assertEquals(expected, json);
  }

  @Test
  public void testEncodeJsonArrayPrettily() {
    JsonArray jsonArray = new JsonArray();
    jsonArray.add("foo");
    jsonArray.add(123);
    jsonArray.add(1234l);
    jsonArray.add(1.23f);
    jsonArray.add(2.34d);
    jsonArray.add(true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String expected = "[ \"foo\", 123, 1234, 1.23, 2.34, true, \"" + strBytes + "\", null, {" + Utils.LINE_SEPARATOR +
      "  \"foo\" : \"bar\"" + Utils.LINE_SEPARATOR +
      "}, [ \"foo\", 123 ] ]";
    String json = jsonArray.encodePrettily();
    assertEquals(expected, json);
  }

  @Test
  public void testDecodeJsonObject() {
    byte[] bytes = TestUtils.randomByteArray(10);
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    Instant now = Instant.now();
    String strInstant = ISO_INSTANT.format(now);
    String json = "{\"mystr\":\"foo\",\"myint\":123,\"mylong\":1234,\"myfloat\":1.23,\"mydouble\":2.34,\"" +
      "myboolean\":true,\"mybinary\":\"" + strBytes + "\",\"myinstant\":\"" + strInstant + "\",\"mynull\":null,\"myobj\":{\"foo\":\"bar\"},\"myarr\":[\"foo\",123]}";
    JsonObject obj = new JsonObject(json);
    assertEquals(json, obj.encode());
    assertEquals("foo", obj.getString("mystr"));
    assertEquals(Integer.valueOf(123), obj.getInteger("myint"));
    assertEquals(Long.valueOf(1234), obj.getLong("mylong"));
    assertEquals(Float.valueOf(1.23f), obj.getFloat("myfloat"));
    assertEquals(Double.valueOf(2.34d), obj.getDouble("mydouble"));
    assertTrue(obj.getBoolean("myboolean"));
    assertArrayEquals(bytes, obj.getBinary("mybinary"));
    assertEquals(Base64.getEncoder().encodeToString(bytes), obj.getValue("mybinary"));
    assertEquals(now, obj.getInstant("myinstant"));
    assertEquals(now.toString(), obj.getValue("myinstant"));
    assertTrue(obj.containsKey("mynull"));
    JsonObject nestedObj = obj.getJsonObject("myobj");
    assertEquals("bar", nestedObj.getString("foo"));
    JsonArray nestedArr = obj.getJsonArray("myarr");
    assertEquals("foo", nestedArr.getString(0));
    assertEquals(Integer.valueOf(123), Integer.valueOf(nestedArr.getInteger(1)));
  }

  @Test
  public void testDecodeJsonArray() {
    byte[] bytes = TestUtils.randomByteArray(10);
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    Instant now = Instant.now();
    String strInstant = ISO_INSTANT.format(now);
    String json = "[\"foo\",123,1234,1.23,2.34,true,\"" + strBytes + "\",\"" + strInstant + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]";
    JsonArray arr = new JsonArray(json);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertEquals(Long.valueOf(1234l), arr.getLong(2));
    assertEquals(Float.valueOf(1.23f), arr.getFloat(3));
    assertEquals(Double.valueOf(2.34d), arr.getDouble(4));
    assertEquals(true, arr.getBoolean(5));
    assertArrayEquals(bytes, arr.getBinary(6));
    assertEquals(Base64.getEncoder().encodeToString(bytes), arr.getValue(6));
    assertEquals(now, arr.getInstant(7));
    assertEquals(now.toString(), arr.getValue(7));
    assertTrue(arr.hasNull(8));
    JsonObject obj = arr.getJsonObject(9);
    assertEquals("bar", obj.getString("foo"));
    JsonArray arr2 = arr.getJsonArray(10);
    assertEquals("foo", arr2.getString(0));
    assertEquals(Integer.valueOf(123), arr2.getInteger(1));
  }

  // Strict JSON doesn't allow comments but we do so users can add comments to config files etc
  @Test
  public void testDecodeJsonObjectWithComments() {
    String jsonWithComments =
      "// single line comment\n" +
        "/*\n" +
        "  This is a multi \n" +
        "  line comment\n" +
        "*/\n" +
        "{\n" +
        "// another single line comment this time inside the JSON object itself\n" +
        "  \"foo\": \"bar\" // and a single line comment at end of line \n" +
        "/*\n" +
        "  This is a another multi \n" +
        "  line comment this time inside the JSON object itself\n" +
        "*/\n" +
        "}";
    JsonObject json = new JsonObject(jsonWithComments);
    assertEquals("{\"foo\":\"bar\"}", json.encode());
  }

  // Strict JSON doesn't allow comments but we do so users can add comments to config files etc
  @Test
  public void testDecodeJsonArrayWithComments() {
    String jsonWithComments =
      "// single line comment\n" +
        "/*\n" +
        "  This is a multi \n" +
        "  line comment\n" +
        "*/\n" +
        "[\n" +
        "// another single line comment this time inside the JSON array itself\n" +
        "  \"foo\", \"bar\" // and a single line comment at end of line \n" +
        "/*\n" +
        "  This is a another multi \n" +
        "  line comment this time inside the JSON array itself\n" +
        "*/\n" +
        "]";
    JsonArray json = new JsonArray(jsonWithComments);
    assertEquals("[\"foo\",\"bar\"]", json.encode());
  }

  @Test
  public void testDecodeJsonObjectWithInvalidJson() {
    for (String test : new String[] { "null", "3", "\"3", "qiwjdoiqwjdiqwjd" }) {
      try {
        new JsonObject(test);
        fail();
      } catch (DecodeException ignore) {
      }
      try {
        new JsonObject(Buffer.buffer(test));
        fail();
      } catch (DecodeException ignore) {
      }
    }
  }

  @Test
  public void testDecodeJsonArrayWithInvalidJson() {
    for (String test : new String[] { "null", "3", "\"3", "qiwjdoiqwjdiqwjd" }) {
      try {
        new JsonArray(test);
        fail();
      } catch (DecodeException ignore) {
      }
      try {
        new JsonArray(Buffer.buffer(test));
        fail();
      } catch (DecodeException ignore) {
      }
    }
  }

  @Test
  public void encodeCustomTypeInstant() {
    Instant now = Instant.now();
    String json = Json.encode(now);
    assertNotNull(json);
    // the RFC is one way only
    Instant decoded = Instant.from(ISO_INSTANT.parse(json.substring(1, json.length() - 1)));
    assertEquals(now, decoded);

  }

  @Test
  public void encodeCustomTypeBinary() {
    byte[] data = new byte[] { 'h', 'e', 'l', 'l', 'o'};
    String json = Json.encode(data);
    assertNotNull(json);
    // base64 encoded hello
    assertEquals("\"aGVsbG8=\"", json);
  }

  @Test
  public void encodeNull() {
    String json = Json.encode(null);
    assertNotNull(json);
    assertEquals("null", json);
  }

  @Test
  public void encodeToBuffer() {
    Buffer json = Json.encodeToBuffer("Hello World!");
    assertNotNull(json);
    // json strings are always UTF8
    assertEquals("\"Hello World!\"", json.toString());
  }

  @Test
  public void encodeNullToBuffer() {
    Buffer json = Json.encodeToBuffer(null);
    assertNotNull(json);
    assertEquals("null", json.toString());
  }

  @Test
  public void testDecodeValue() {
    assertDecodeValue(Buffer.buffer("42"), 42, INTEGER_TYPE_REF);
    assertDecodeValue(Buffer.buffer("42"), 42L, LONG_TYPE_REF);
    assertDecodeValue(Buffer.buffer("\"foobar\""), "foobar", STRING_TYPE_REF);
    assertDecodeValue(Buffer.buffer("3.4"), 3.4f, FLOAT_TYPE_REF);
    assertDecodeValue(Buffer.buffer("3.4"), 3.4d, DOUBLE_TYPE_REF);
    assertDecodeValue(Buffer.buffer("{\"foo\":4}"), Collections.singletonMap("foo", 4), MAP_TYPE_REF);
    assertDecodeValue(Buffer.buffer("[0,1,2]"), Arrays.asList(0, 1, 2), LIST_TYPE_REF);
    assertDecodeValue(Buffer.buffer("true"), true, BOOLEAN_TYPE_REF);
    assertDecodeValue(Buffer.buffer("false"), false, BOOLEAN_TYPE_REF);
  }

  private <T> void assertDecodeValue(Buffer buffer, T expected, TypeReference<T> ref) {
    Type type = ref.getType();
    Class<?> clazz = type instanceof Class ? (Class<?>) type : (Class<?>) ((ParameterizedType) type).getRawType();
    assertEquals(expected, Json.decodeValue(buffer, clazz));
    assertEquals(expected, Json.decodeValue(buffer, ref));
    assertEquals(expected, Json.decodeValue(buffer.toString(StandardCharsets.UTF_8), clazz));
    assertEquals(expected, Json.decodeValue(buffer.toString(StandardCharsets.UTF_8), ref));
    Buffer nullValue = Buffer.buffer("null");
    assertNull(Json.decodeValue(nullValue, clazz));
    assertNull(Json.decodeValue(nullValue, ref));
    assertNull(Json.decodeValue(nullValue.toString(StandardCharsets.UTF_8), clazz));
    assertNull(Json.decodeValue(nullValue.toString(StandardCharsets.UTF_8), ref));
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
    assertEquals(1, asBuffer ? Json.decodeValue(Buffer.buffer(number)) : Json.decodeValue(number));

    String bool = Boolean.TRUE.toString();
    assertEquals(true, asBuffer ? Json.decodeValue(Buffer.buffer(bool)) : Json.decodeValue(bool));

    String text = "\"whatever\"";
    assertEquals("whatever", asBuffer ? Json.decodeValue(Buffer.buffer(text)) : Json.decodeValue(text));

    String nullText = "null";
    assertNull(asBuffer ? Json.decodeValue(Buffer.buffer(nullText)) : Json.decodeValue(nullText));

    JsonObject obj = new JsonObject().put("foo", "bar");
    assertEquals(obj, asBuffer ? Json.decodeValue(obj.toBuffer()) : Json.decodeValue(obj.toString()));

    JsonArray arr = new JsonArray().add(1).add(false).add("whatever").add(obj);
    assertEquals(arr, asBuffer ? Json.decodeValue(arr.toBuffer()) : Json.decodeValue(arr.toString()));

    String invalidText = "\"invalid";
    try {
      if (asBuffer) {
        Json.decodeValue(Buffer.buffer(invalidText));
      } else {
        Json.decodeValue(invalidText);
      }
      fail();
    } catch (DecodeException ignore) {
    }
  }
}
