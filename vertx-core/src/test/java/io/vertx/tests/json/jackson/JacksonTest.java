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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.core.StreamReadConstraints.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonTest extends VertxTestBase {

  private static final TypeReference<Integer> INTEGER_TYPE_REF = new TypeReference<Integer>() {};
  private static final TypeReference<Long> LONG_TYPE_REF = new TypeReference<Long>() {};
  private static final TypeReference<String> STRING_TYPE_REF = new TypeReference<String>() {};
  private static final TypeReference<Float> FLOAT_TYPE_REF = new TypeReference<Float>() {};
  private static final TypeReference<Double> DOUBLE_TYPE_REF = new TypeReference<Double>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {};
  private static final TypeReference<List<Object>> LIST_TYPE_REF = new TypeReference<List<Object>>() {};
  private static final TypeReference<Boolean> BOOLEAN_TYPE_REF = new TypeReference<Boolean>() {};

  private final JacksonCodec codec = new JacksonCodec();

  public static class MyPojo {
  }

  @Test
  public void testEncodeUnknownNumber() {
    String result = codec.toString(new Number() {
      @Override
      public int intValue() {
        throw new UnsupportedOperationException();
      }
      @Override
      public long longValue() {
        throw new UnsupportedOperationException();
      }
      @Override
      public float floatValue() {
        throw new UnsupportedOperationException();
      }
      @Override
      public double doubleValue() {
        return 4D;
      }
    });
    assertEquals("4.0", result);
  }

  @Test
  public void testEncodePojoFailure() {
    try {
      codec.toString(new MyPojo());
      fail();
    } catch (EncodeException e) {
      assertTrue(e.getMessage().contains(MyPojo.class.getName()));
    }
  }

  @Test(expected = EncodeException.class)
  public void encodeToBuffer() {
    // if other than EncodeException happens here, then
    // there is probably a leak closing the netty buffer output stream
    codec.toBuffer(new RuntimeException("Unsupported"));
  }

  @Test
  public void testDefaultConstraints() {
    testReadConstraints(
      DEFAULT_MAX_DEPTH,
      DEFAULT_MAX_NUM_LEN,
      DEFAULT_MAX_STRING_LEN,
      DEFAULT_MAX_NAME_LEN,
      DEFAULT_MAX_DOC_LEN);
  }

  public static void testReadConstraints(int defaultMaxDepth,
                                         int maxNumberLength,
                                         int defaultMaxStringLength,
                                         int defaultMaxNameLength,
                                         long defaultMaxDocumentLength) {
    testMaxNestingDepth(defaultMaxDepth);
    try {
      testMaxNestingDepth(defaultMaxDepth + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }
    testMaxNumberLength(maxNumberLength);
    try {
      testMaxNumberLength(maxNumberLength + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }

    testMaxStringLength(defaultMaxStringLength);
    try {
      testMaxStringLength(defaultMaxStringLength + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }

    testMaxNameLength(defaultMaxNameLength);
    try {
      testMaxNameLength(defaultMaxNameLength + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }

    if (defaultMaxDocumentLength >= 0) {
      testMaxDocumentLength(defaultMaxDocumentLength);
      try {
        testMaxDocumentLength(defaultMaxDocumentLength + 1);
        Assert.fail();
      } catch (DecodeException expected) {
      }
    }
  }

  private static JsonArray testMaxNestingDepth(int depth) {
    String json = "[".repeat(depth) + "]".repeat(depth);
    return new JsonArray(json);
  }

  private static JsonObject testMaxNumberLength(int len) {
    String json = "{\"number\":" + "1".repeat(len) + "}";
    return new JsonObject(json);
  }

  private static JsonObject testMaxStringLength(int len) {
    String json = "{\"string\":\"" + "a".repeat(len) + "\"}";
    return new JsonObject(json);
  }

  private static JsonObject testMaxNameLength(int len) {
    String json = "{\"" + "a".repeat(len) + "\":3}";
    return new JsonObject(json);
  }

  private static JsonArray testMaxDocumentLength(long len) {
    String prefix = len % 2 == 0 ? "[ " : "[";
    int num = (int) ((len - prefix.length()) / 2);
    StringBuilder sb = new StringBuilder((int) len);
    sb.append(prefix);
    for (int i = 0; i < num;i++) {
      sb.append("0,");
    }
    sb.setCharAt((int) (len - 1), ']');
    String json = sb.toString();
    return new JsonArray(json);
  }

  @Test
  public void testParseMap() throws Exception {
    JsonParser parser = JacksonCodec.createParser("{\"nested\":{\"key\":\"value\"},\"another\":4}");
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    Map<String, Object> nested = JacksonCodec.parseObject(parser);
    assertEquals(Map.of("key", "value"), nested);
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_NUMBER_INT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_END_OBJECT, parser.nextToken().id());
  }

  @Test
  public void testParseAny() throws Exception {
    JsonParser parser = JacksonCodec.createParser("{\"nested\":{\"key\":\"value\"},\"another\":4}");
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    Object nested = JacksonCodec.parseValue(parser);
    assertEquals(Map.of("key", "value"), nested);
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_NUMBER_INT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_END_OBJECT, parser.nextToken().id());
  }

  @Test
  public void testParseArray() throws Exception {
    JsonParser parser = JacksonCodec.createParser("{\"nested\":[0,1,2],\"another\":4}");
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_START_ARRAY, parser.nextToken().id());
    Object nested = JacksonCodec.parseArray(parser);
    assertEquals(Arrays.asList(0, 1, 2), nested);
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_NUMBER_INT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_END_OBJECT, parser.nextToken().id());
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
}
