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

package io.vertx.tests.json.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.ThreadingModel;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JacksonDatabindTest extends JacksonDatabindTestBase {

  @Override
  protected String writeValueAsString(Object o) throws Exception {
    return DatabindCodec.mapper().writeValueAsString(o);
  }

  @Override
  protected <T> T readValue(String content, Class<T> valueType) throws Exception {
    return DatabindCodec.mapper().readValue(content, valueType);
  }

  @Test
  public void testGetMapper() {
    ObjectMapper mapper = DatabindCodec.mapper();
    assertNotNull(mapper);
  }

  @Test
  public void testGenericDecoding() {
    Pojo original = new Pojo();
    original.value = "test";

    String json = Json.encode(Collections.singletonList(original));
    List<Pojo> correct;

    DatabindCodec databindCodec = new DatabindCodec();

    correct = databindCodec.fromString(json, new TypeReference<>() {
    });
    assertTrue(((List) correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    // same must apply if instead of string we use a buffer
    correct = databindCodec.fromBuffer(Buffer.buffer(json, "UTF8"), new TypeReference<>() {
    });
    assertTrue(((List) correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    List incorrect = Json.decodeValue(json, List.class);
    assertFalse(incorrect.get(0) instanceof Pojo);
    assertTrue(incorrect.get(0) instanceof Map);
    assertEquals(original.value, ((Map) (incorrect.get(0))).get("value"));
  }

  @Test
  public void testObjectMapperConfigAppliesToPrettyPrinting() {
    ObjectMapper om = DatabindCodec.mapper();
    SerializationConfig sc = om.getSerializationConfig();
    assertNotNull(sc);
    try {
      om.setConfig(sc.with(SerializationFeature.WRITE_ENUMS_USING_INDEX));
      ThreadingModel vt = ThreadingModel.VIRTUAL_THREAD;
      String expected = String.valueOf(vt.ordinal());
      assertEquals(expected, Json.encodePrettily(vt));
      assertEquals(expected, Json.encode(vt));
    } finally {
      om.setConfig(sc);
    }
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
    DatabindCodec codec = new DatabindCodec();
    assertDecodeValue(codec, Buffer.buffer("42"), 42, INTEGER_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("42"), 42L, LONG_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("\"foobar\""), "foobar", STRING_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("3.4"), 3.4f, FLOAT_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("3.4"), 3.4d, DOUBLE_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("{\"foo\":4}"), Collections.singletonMap("foo", 4), MAP_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("[0,1,2]"), Arrays.asList(0, 1, 2), LIST_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("true"), true, BOOLEAN_TYPE_REF);
    assertDecodeValue(codec, Buffer.buffer("false"), false, BOOLEAN_TYPE_REF);
  }

  private <T> void assertDecodeValue(DatabindCodec codec, Buffer buffer, T expected, TypeReference<T> ref) {
    Type type = ref.getType();
    Class<?> clazz = type instanceof Class ? (Class<?>) type : (Class<?>) ((ParameterizedType) type).getRawType();
    assertEquals(expected, codec.fromBuffer(buffer, clazz));
    assertEquals(expected, codec.fromBuffer(buffer, ref));
    assertEquals(expected, codec.fromString(buffer.toString(StandardCharsets.UTF_8), clazz));
    assertEquals(expected, codec.fromString(buffer.toString(StandardCharsets.UTF_8), ref));
    Buffer nullValue = Buffer.buffer("null");
    assertNull(codec.fromBuffer(nullValue, clazz));
    assertNull(codec.fromBuffer(nullValue, ref));
    assertNull(codec.fromString(nullValue.toString(StandardCharsets.UTF_8), clazz));
    assertNull(codec.fromString(nullValue.toString(StandardCharsets.UTF_8), ref));
  }
}
