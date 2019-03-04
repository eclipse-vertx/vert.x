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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonMapperTest extends VertxTestBase {

  @Test
  public void testGetSetMapper() {
    ObjectMapper mapper = Json.mapper;
    assertNotNull(mapper);
    ObjectMapper newMapper = new ObjectMapper();
    Json.mapper = newMapper;
    assertSame(newMapper, Json.mapper);
    Json.mapper = mapper;
  }

  @Test
  public void testGetSetPrettyMapper() {
    ObjectMapper mapper = Json.prettyMapper;
    assertNotNull(mapper);
    ObjectMapper newMapper = new ObjectMapper();
    Json.prettyMapper = newMapper;
    assertSame(newMapper, Json.prettyMapper);
    Json.prettyMapper = mapper;
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
  public void encodeCustomTypeInstantNull() {
    Instant now = null;
    String json = Json.encode(now);
    assertNotNull(json);
    assertEquals("null", json);
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
  public void encodeCustomTypeBinaryNull() {
    byte[] data = null;
    String json = Json.encode(data);
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
  public void testGenericDecoding() {
    Pojo original = new Pojo();
    original.value = "test";

    String json = Json.encode(Collections.singletonList(original));
    List<Pojo> correct;

    correct = Json.decodeValue(json, new TypeReference<List<Pojo>>() {});
    assertTrue(((List)correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    // same must apply if instead of string we use a buffer
    correct = Json.decodeValue(Buffer.buffer(json, "UTF8"), new TypeReference<List<Pojo>>() {});
    assertTrue(((List)correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    List incorrect = Json.decodeValue(json, List.class);
    assertFalse(incorrect.get(0) instanceof Pojo);
    assertTrue(incorrect.get(0) instanceof Map);
    assertEquals(original.value, ((Map)(incorrect.get(0))).get("value"));
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

  private static class Pojo {
    @JsonProperty
    String value;
    @JsonProperty
    Instant instant;
    @JsonProperty
    byte[] bytes;
  }

  private static final TypeReference<Integer> INTEGER_TYPE_REF = new TypeReference<Integer>() {};
  private static final TypeReference<Long> LONG_TYPE_REF = new TypeReference<Long>() {};
  private static final TypeReference<String> STRING_TYPE_REF = new TypeReference<String>() {};
  private static final TypeReference<Float> FLOAT_TYPE_REF = new TypeReference<Float>() {};
  private static final TypeReference<Double> DOUBLE_TYPE_REF = new TypeReference<Double>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {};
  private static final TypeReference<List<Object>> LIST_TYPE_REF = new TypeReference<List<Object>>() {};
  private static final TypeReference<Boolean> BOOLEAN_TYPE_REF = new TypeReference<Boolean>() {};

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
