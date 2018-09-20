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
import io.vertx.core.json.Json;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

  private static class Pojo {
    @JsonProperty
    String value;
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
}
