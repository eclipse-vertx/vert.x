/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import org.junit.Test;

import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

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
}
