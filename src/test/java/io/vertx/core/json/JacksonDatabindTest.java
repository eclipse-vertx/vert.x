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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.time.Instant;
import java.util.*;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JacksonDatabindTest extends VertxTestBase {

  @Test
  public void testGetMapper() {
    ObjectMapper mapper = DatabindCodec.mapper();
    assertNotNull(mapper);
  }

  @Test
  public void testGetPrettyMapper() {
    ObjectMapper mapper = DatabindCodec.prettyMapper();
    assertNotNull(mapper);
  }

  @Test
  public void testGenericDecoding() {
    Pojo original = new Pojo();
    original.value = "test";

    String json = Json.encode(Collections.singletonList(original));
    List<Pojo> correct;

    correct = JacksonCodec.decodeValue(json, new TypeReference<List<Pojo>>() {});
    assertTrue(((List)correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    // same must apply if instead of string we use a buffer
    correct = JacksonCodec.decodeValue(Buffer.buffer(json, "UTF8"), new TypeReference<List<Pojo>>() {});
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
}
