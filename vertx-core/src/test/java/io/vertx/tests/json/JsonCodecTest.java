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
package io.vertx.tests.json;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.WebSocketVersion;
import io.vertx.core.impl.Utils;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.test.core.TestUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class JsonCodecTest {

  private static final TypeReference<Integer> INTEGER_TYPE_REF = new TypeReference<Integer>() {};
  private static final TypeReference<Long> LONG_TYPE_REF = new TypeReference<Long>() {};
  private static final TypeReference<String> STRING_TYPE_REF = new TypeReference<String>() {};
  private static final TypeReference<Float> FLOAT_TYPE_REF = new TypeReference<Float>() {};
  private static final TypeReference<Double> DOUBLE_TYPE_REF = new TypeReference<Double>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {};
  private static final TypeReference<List<Object>> LIST_TYPE_REF = new TypeReference<List<Object>>() {};
  private static final TypeReference<Boolean> BOOLEAN_TYPE_REF = new TypeReference<Boolean>() {};

  @Parameterized.Parameters
  public static Collection<Object[]> mappers() {
    return Arrays.asList(new Object[][] {
      { new DatabindCodec() }, { new JacksonCodec() }
    });
  }

  private final JacksonCodec codec;

  public JsonCodecTest(JacksonCodec codec) {
    this.codec = codec;
  }

  @Test
  public void testEncodeJsonObject() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("mystr", "foo");
    jsonObject.put("myint", 123);
    jsonObject.put("mylong", 1234l);
    jsonObject.put("myfloat", 1.23f);
    jsonObject.put("mydouble", 2.34d);
    jsonObject.put("myboolean", true);
    jsonObject.put("mybyte", 255);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonObject.put("mybinary", bytes);
    jsonObject.put("mybuffer", Buffer.buffer(bytes));
    Instant now = Instant.now();
    jsonObject.put("myinstant", now);
    jsonObject.putNull("mynull");
    jsonObject.put("myobj", new JsonObject().put("foo", "bar"));
    jsonObject.put("myarr", new JsonArray().add("foo").add(123));
    String strBytes = TestUtils.toBase64String(bytes);
    String expected = "{\"mystr\":\"foo\",\"myint\":123,\"mylong\":1234,\"myfloat\":1.23,\"mydouble\":2.34,\"" +
      "myboolean\":true,\"mybyte\":255,\"mybinary\":\"" + strBytes + "\",\"mybuffer\":\"" + strBytes + "\",\"myinstant\":\"" + ISO_INSTANT.format(now) + "\",\"mynull\":null,\"myobj\":{\"foo\":\"bar\"},\"myarr\":[\"foo\",123]}";
    String json = codec.toString(jsonObject);
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
    jsonArray.add((byte)124);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    jsonArray.add(Buffer.buffer(bytes));
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = TestUtils.toBase64String(bytes);
    String expected = "[\"foo\",123,1234,1.23,2.34,true,124,\"" + strBytes + "\",\"" + strBytes + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]";
    String json = codec.toString(jsonArray);
    assertEquals(expected, json);
  }

  @Test
  public void testEncodeJsonObjectToBuffer() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("mystr", "foo");
    jsonObject.put("myint", 123);
    jsonObject.put("mylong", 1234l);
    jsonObject.put("myfloat", 1.23f);
    jsonObject.put("mydouble", 2.34d);
    jsonObject.put("myboolean", true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonObject.put("mybinary", bytes);
    jsonObject.put("mybuffer", Buffer.buffer(bytes));
    Instant now = Instant.now();
    jsonObject.put("myinstant", now);
    jsonObject.putNull("mynull");
    jsonObject.put("myobj", new JsonObject().put("foo", "bar"));
    jsonObject.put("myarr", new JsonArray().add("foo").add(123));
    String strBytes = TestUtils.toBase64String(bytes);

    Buffer expected = Buffer.buffer("{\"mystr\":\"foo\",\"myint\":123,\"mylong\":1234,\"myfloat\":1.23,\"mydouble\":2.34,\"" +
      "myboolean\":true,\"mybinary\":\"" + strBytes + "\",\"mybuffer\":\"" + strBytes + "\",\"myinstant\":\"" + ISO_INSTANT.format(now) + "\",\"mynull\":null,\"myobj\":{\"foo\":\"bar\"},\"myarr\":[\"foo\",123]}", "UTF-8");

    Buffer json = codec.toBuffer(jsonObject);
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
    jsonArray.add(Buffer.buffer(bytes));
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = TestUtils.toBase64String(bytes);
    Buffer expected = Buffer.buffer("[\"foo\",123,1234,1.23,2.34,true,\"" + strBytes + "\",\"" + strBytes + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]", "UTF-8");
    Buffer json = codec.toBuffer(jsonArray);
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
    jsonObject.put("mybuffer", Buffer.buffer(bytes));
    Instant now = Instant.now();
    jsonObject.put("myinstant", now);
    jsonObject.put("myobj", new JsonObject().put("foo", "bar"));
    jsonObject.put("myarr", new JsonArray().add("foo").add(123));
    String strBytes = TestUtils.toBase64String(bytes);
    String strInstant = ISO_INSTANT.format(now);
    String expected = "{" + Utils.LINE_SEPARATOR +
      "  \"mystr\" : \"foo\"," + Utils.LINE_SEPARATOR +
      "  \"myint\" : 123," + Utils.LINE_SEPARATOR +
      "  \"mylong\" : 1234," + Utils.LINE_SEPARATOR +
      "  \"myfloat\" : 1.23," + Utils.LINE_SEPARATOR +
      "  \"mydouble\" : 2.34," + Utils.LINE_SEPARATOR +
      "  \"myboolean\" : true," + Utils.LINE_SEPARATOR +
      "  \"mybinary\" : \"" + strBytes + "\"," + Utils.LINE_SEPARATOR +
      "  \"mybuffer\" : \"" + strBytes + "\"," + Utils.LINE_SEPARATOR +
      "  \"myinstant\" : \"" + strInstant + "\"," + Utils.LINE_SEPARATOR +
      "  \"myobj\" : {" + Utils.LINE_SEPARATOR +
      "    \"foo\" : \"bar\"" + Utils.LINE_SEPARATOR +
      "  }," + Utils.LINE_SEPARATOR +
      "  \"myarr\" : [ \"foo\", 123 ]" + Utils.LINE_SEPARATOR +
      "}";
    String json = codec.toString(jsonObject, true);
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
    jsonArray.add(Buffer.buffer(bytes));
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = TestUtils.toBase64String(bytes);
    String expected = "[ \"foo\", 123, 1234, 1.23, 2.34, true, \"" + strBytes + "\", \"" + strBytes + "\", null, {" + Utils.LINE_SEPARATOR +
      "  \"foo\" : \"bar\"" + Utils.LINE_SEPARATOR +
      "}, [ \"foo\", 123 ] ]";
    String json = codec.toString(jsonArray, true);
    assertEquals(expected, json);
  }

  @Test
  public void testDecodeJsonObject() {
    byte[] bytes = TestUtils.randomByteArray(10);
    String strBytes = TestUtils.toBase64String(bytes);
    Instant now = Instant.now();
    String strInstant = ISO_INSTANT.format(now);
    String json = "{\"mystr\":\"foo\",\"myint\":123,\"mylong\":1234,\"myfloat\":1.23,\"mydouble\":2.34,\"" +
      "myboolean\":true,\"mybyte\":124,\"mybinary\":\"" + strBytes + "\",\"mybuffer\":\"" + strBytes + "\",\"myinstant\":\"" + strInstant + "\",\"mynull\":null,\"myobj\":{\"foo\":\"bar\"},\"myarr\":[\"foo\",123]}";
    JsonObject obj = new JsonObject(codec.fromString(json, Map.class));
    assertEquals(json, codec.toString(obj));
    assertEquals("foo", obj.getString("mystr"));
    assertEquals(Integer.valueOf(123), obj.getInteger("myint"));
    assertEquals(Long.valueOf(1234), obj.getLong("mylong"));
    assertEquals(Float.valueOf(1.23f), obj.getFloat("myfloat"));
    assertEquals(Double.valueOf(2.34d), obj.getDouble("mydouble"));
    assertTrue(obj.getBoolean("myboolean"));
    assertEquals(124, obj.getValue("mybyte"));
    assertArrayEquals(bytes, obj.getBinary("mybinary"));
    assertEquals(Buffer.buffer(bytes), obj.getBuffer("mybuffer"));
    assertEquals(TestUtils.toBase64String(bytes), obj.getValue("mybinary"));
    assertEquals(TestUtils.toBase64String(bytes), obj.getValue("mybuffer"));
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
    String strBytes = TestUtils.toBase64String(bytes);
    Instant now = Instant.now();
    String strInstant = ISO_INSTANT.format(now);
    String json = "[\"foo\",123,1234,1.23,2.34,true,124,\"" + strBytes + "\",\"" + strBytes + "\",\"" + strInstant + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]";
    JsonArray arr = new JsonArray(codec.fromString(json, List.class));
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertEquals(Long.valueOf(1234l), arr.getLong(2));
    assertEquals(Float.valueOf(1.23f), arr.getFloat(3));
    assertEquals(Double.valueOf(2.34d), arr.getDouble(4));
    assertEquals(true, arr.getBoolean(5));
    assertEquals(124, arr.getValue(6));
    assertArrayEquals(bytes, arr.getBinary(7));
    assertEquals(TestUtils.toBase64String(bytes), arr.getValue(7));
    assertEquals(Buffer.buffer(bytes), arr.getBuffer(8));
    assertEquals(TestUtils.toBase64String(bytes), arr.getValue(8));
    assertEquals(now, arr.getInstant(9));
    assertEquals(now.toString(), arr.getValue(9));
    assertTrue(arr.hasNull(10));
    JsonObject obj = arr.getJsonObject(11);
    assertEquals("bar", obj.getString("foo"));
    JsonArray arr2 = arr.getJsonArray(12);
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
    JsonObject json = new JsonObject(codec.fromString(jsonWithComments, Map.class));
    assertEquals("{\"foo\":\"bar\"}", codec.toString(json));
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
    JsonArray json = new JsonArray(codec.fromString(jsonWithComments, List.class));
    assertEquals("[\"foo\",\"bar\"]", codec.toString(json));
  }

  @Test
  public void testDecodeJsonObjectWithInvalidJson() {
    for (String test : new String[] { "3", "\"3", "qiwjdoiqwjdiqwjd", "{\"foo\":1},{\"bar\":2}", "{\"foo\":1} 1234" }) {
      try {
        codec.fromString(test, Map.class);
        fail();
      } catch (DecodeException ignore) {
      }
      try {
        codec.fromBuffer(Buffer.buffer(test), Map.class);
        fail();
      } catch (DecodeException ignore) {
      }
    }
  }

  @Test
  public void testDecodeJsonArrayWithInvalidJson() {
    for (String test : new String[] { "3", "\"3", "qiwjdoiqwjdiqwjd", "[1],[2]", "[] 1234" }) {
      try {
        codec.fromString(test, List.class);
        fail();
      } catch (DecodeException ignore) {
      }
      try {
        codec.fromBuffer(Buffer.buffer(test), List.class);
        fail();
      } catch (DecodeException ignore) {
      }
    }
  }

  @Test
  public void encodeCustomTypeInstant() {
    Instant now = Instant.now();
    String json = codec.toString(now);
    assertNotNull(json);
    // the RFC is one way only
    Instant decoded = Instant.from(ISO_INSTANT.parse(json.substring(1, json.length() - 1)));
    assertEquals(now, decoded);
  }

  @Test
  public void decodeCustomTypeInstant() {
    Instant now = Instant.now();
    String json = '"' + ISO_INSTANT.format(now) + '"';
    Instant decoded = codec.fromString(json, Instant.class);
    assertEquals(now, decoded);
  }

  @Test
  public void encodeCustomTypeBinary() {
    byte[] data = new byte[] { 'h', 'e', 'l', 'l', 'o'};
    String json = codec.toString(data);
    assertNotNull(json);
    assertEquals("\"aGVsbG8\"", json);
    json = codec.toString(Buffer.buffer(data));
    assertNotNull(json);
    assertEquals("\"aGVsbG8\"", json);
  }

  @Test
  public void decodeCustomTypeBinary() {
    // base64 encoded hello
    byte[] data = codec.fromString("\"aGVsbG8\"", byte[].class);
    assertEquals("hello", new String(data));
    Buffer buff = codec.fromString("\"aGVsbG8\"", Buffer.class);
    assertEquals("hello", buff.toString());
  }

  @Test
  public void encodeNull() {
    String json = codec.toString(null);
    assertNotNull(json);
    assertEquals("null", json);
  }

  @Test
  public void encodeToBuffer() {
    Buffer json = codec.toBuffer("Hello World!");
    assertNotNull(json);
    // json strings are always UTF8
    assertEquals("\"Hello World!\"", json.toString());
  }

  @Test
  public void encodeNullToBuffer() {
    Buffer json = codec.toBuffer(null);
    assertNotNull(json);
    assertEquals("null", json.toString());
  }

  @Test
  public void testDecodeValue() {
    Assume.assumeTrue(codec instanceof DatabindCodec);
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

  @Test
  public void testEnumValue() {
    // just a random enum
    Buffer json = codec.toBuffer(WebSocketVersion.V13);
    assertNotNull(json);
    assertEquals("\"V13\"", json.toString());
    codec.fromBuffer(json, WebSocketVersion.class);
  }

  @Test
  public void testBigNumberValues() {
    Buffer json = codec.toBuffer(new BigDecimal("124567890124567890.09876543210987654321"));
    assertNotNull(json);
    assertEquals("124567890124567890.09876543210987654321", json.toString());
    Buffer json2 = codec.toBuffer(new BigInteger("12456789009876543211245678900987654321"));
    assertNotNull(json2);
    assertEquals("12456789009876543211245678900987654321", json2.toString());
  }

  private <T> void assertDecodeValue(Buffer buffer, T expected, TypeReference<T> ref) {
    DatabindCodec databindCodec = (DatabindCodec) codec;
    Type type = ref.getType();
    Class<?> clazz = type instanceof Class ? (Class<?>) type : (Class<?>) ((ParameterizedType) type).getRawType();
    assertEquals(expected, codec.fromBuffer(buffer, clazz));
    assertEquals(expected, databindCodec.fromBuffer(buffer, ref));
    assertEquals(expected, codec.fromString(buffer.toString(StandardCharsets.UTF_8), clazz));
    assertEquals(expected, databindCodec.fromString(buffer.toString(StandardCharsets.UTF_8), ref));
    Buffer nullValue = Buffer.buffer("null");
    assertNull(codec.fromBuffer(nullValue, clazz));
    assertNull(databindCodec.fromBuffer(nullValue, ref));
    assertNull(codec.fromString(nullValue.toString(StandardCharsets.UTF_8), clazz));
    assertNull(databindCodec.fromString(nullValue.toString(StandardCharsets.UTF_8), ref));
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
    assertEquals(1, asBuffer ? codec.fromBuffer(Buffer.buffer(number)) : codec.fromString(number));

    String bool = Boolean.TRUE.toString();
    assertEquals(true, asBuffer ? codec.fromBuffer(Buffer.buffer(bool)) : codec.fromString(bool));

    String text = "\"whatever\"";
    assertEquals("whatever", asBuffer ? codec.fromBuffer(Buffer.buffer(text)) : codec.fromString(text));

    String nullText = "null";
    assertNull(asBuffer ? codec.fromBuffer(Buffer.buffer(nullText)) : codec.fromString(nullText));

    JsonObject obj = new JsonObject().put("foo", "bar");
    assertEquals(obj, asBuffer ? codec.fromBuffer(obj.toBuffer()) : codec.fromString(obj.toString()));

    JsonArray arr = new JsonArray().add(1).add(false).add("whatever").add(obj);
    assertEquals(arr, asBuffer ? codec.fromBuffer(arr.toBuffer()) : codec.fromString(arr.toString()));

    String invalidText = "\"invalid";
    try {
      if (asBuffer) {
        codec.fromBuffer(Buffer.buffer(invalidText));
      } else {
        codec.fromString(invalidText);
      }
      fail();
    } catch (DecodeException ignore) {
    }
  }

  @Test
  public void testEncodeCollectionState() {
    assertEquals("{\"key\":\"QQ\"}", checkMap(new byte[] { 'A' }));
    assertEquals("[\"QQ\"]", checkList(new byte[] { 'A' }));
    assertEquals("{\"key\":\"QQ\"}", checkMap(Buffer.buffer("A")));
    assertEquals("[\"QQ\"]", checkList(Buffer.buffer("A")));
    Instant instant = Instant.ofEpochMilli(0);
    assertEquals("{\"key\":\"1970-01-01T00:00:00Z\"}", checkMap(instant));
    assertEquals("[\"1970-01-01T00:00:00Z\"]", checkList(instant));
    assertEquals("{\"key\":\"MICROSECONDS\"}", checkMap(TimeUnit.MICROSECONDS));
    assertEquals("[\"MICROSECONDS\"]", checkList(TimeUnit.MICROSECONDS));
    BigInteger bigInt = new BigInteger("123456789");
    assertEquals("{\"key\":123456789}", checkMap(bigInt));
    assertEquals("[123456789]", checkList(bigInt));
    BigDecimal bigDec = new BigDecimal(bigInt).divide(new BigDecimal("100"));
    assertEquals("{\"key\":1234567.89}", checkMap(bigDec));
    assertEquals("[1234567.89]", checkList(bigDec));
    assertEquals("{\"key\":{\"foo\":\"bar\"}}", checkMap(new JsonObject().put("foo", "bar")));
    assertEquals("[{\"foo\":\"bar\"}]", checkList(new JsonObject().put("foo", "bar")));
    assertEquals("{\"key\":[\"foo\"]}", checkMap(new JsonArray().add("foo")));
    assertEquals("[[\"foo\"]]", checkList(new JsonArray().add("foo")));
    Locale locale = Locale.FRANCE;
    if (codec instanceof DatabindCodec) {
      assertEquals("{\"key\":\"fr_FR\"}", checkMap(locale));
      assertEquals("[\"fr_FR\"]", checkList(locale));
    } else {
      CharSequence cs = HttpHeaders.ACCEPT;
      assertFalse(cs instanceof String);
      try {
        checkMap(cs);
        fail();
      } catch (EncodeException ignore) {
      }
      try {
        checkList(cs);
        fail();
      } catch (EncodeException ignore) {
      }
      try {
        checkMap(locale);
        fail();
      } catch (EncodeException ignore) {
      }
      try {
        checkList(locale);
        fail();
      } catch (EncodeException ignore) {
      }
    }
  }

  private String checkMap(Object o) {
    Map<String, Object> map = new HashMap<>();
    map.put("key", o);
    return codec.toString(map, false);
  }

  private String checkList(Object o) {
    return codec.toString(Collections.singletonList(o), false);
  }

}
