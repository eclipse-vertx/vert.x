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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.ThreadingModel;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
  public void testGenericDecoding() {
    Pojo original = new Pojo();
    original.value = "test";

    String json = Json.encode(Collections.singletonList(original));
    List<Pojo> correct;

    DatabindCodec databindCodec = new DatabindCodec();

    correct = databindCodec.fromString(json, new TypeReference<List<Pojo>>() {
    });
    assertTrue(((List) correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    // same must apply if instead of string we use a buffer
    correct = databindCodec.fromBuffer(Buffer.buffer(json, "UTF8"), new TypeReference<List<Pojo>>() {
    });
    assertTrue(((List) correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    List incorrect = Json.decodeValue(json, List.class);
    assertFalse(incorrect.get(0) instanceof Pojo);
    assertTrue(incorrect.get(0) instanceof Map);
    assertEquals(original.value, ((Map) (incorrect.get(0))).get("value"));
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
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Pojo original = new Pojo();
    original.bytes = TestUtils.randomByteArray(12);
    Pojo decoded = Json.decodeValue("{\"bytes\":\"" + TestUtils.toBase64String(original.bytes) + "\"}", Pojo.class);
    assertArrayEquals(original.bytes, decoded.bytes);
  }

  @Test
  public void testNullBytesDecoding() {
    Pojo original = new Pojo();
    Pojo decoded = Json.decodeValue("{\"bytes\":null}", Pojo.class);
    assertEquals(original.bytes, decoded.bytes);
  }


  @Test
  public void testJsonArrayDeserializer() throws JsonProcessingException {

    String jsonArrayString = "[1, 2, 3]";
    JsonArray jsonArray = DatabindCodec.mapper().readValue(jsonArrayString, JsonArray.class);

    assertEquals(3, jsonArray.size());
    assertEquals(new JsonArray().add(1).add(2).add(3), jsonArray);

  }


  @Test
  public void testJsonObjectDeserializer() throws JsonProcessingException {

    String jsonObjectString = "{\"key1\": \"value1\", \"key2\": \"value2\", \"key3\": \"value3\"}";

    JsonObject jsonObject = DatabindCodec.mapper().readValue(jsonObjectString, JsonObject.class);

    assertEquals("value1", jsonObject.getString("key1"));
    assertEquals("value2", jsonObject.getString("key2"));

  }

  @Test
  public void testJsonObjectSerializer() throws JsonProcessingException {

    ObjectMapper objectMapper = DatabindCodec.mapper();

    JsonObject jsonObject = new JsonObject();
    jsonObject.put("key1", "value1");
    jsonObject.put("key2", "value2");
    jsonObject.put("key3", "value3");

    String jsonString = objectMapper.writeValueAsString(jsonObject);

    assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}", jsonString);
  }

  @Test
  public void testJsonArraySerializer() throws JsonProcessingException {


    JsonArray jsonArray = new JsonArray();
    jsonArray.add("value1");
    jsonArray.add("value2");
    jsonArray.add("value3");

    String jsonString = DatabindCodec.mapper().writeValueAsString(jsonArray);

    assertEquals("[\"value1\",\"value2\",\"value3\"]", jsonString);
  }

  @Test
  public void testInstantSerializer() throws IOException {

    Instant instant = Instant.parse("2023-06-09T12:34:56.789Z");

    String jsonString = DatabindCodec.mapper().writeValueAsString(instant);

    assertEquals("\"2023-06-09T12:34:56.789Z\"", jsonString);
  }

  @Test
  public void testInstantDeserializer() throws IOException {

    String jsonString = "\"2023-06-09T12:34:56.789Z\"";

    Instant instant = DatabindCodec.mapper().readValue(jsonString, Instant.class);

    Instant expectedInstant = Instant.parse("2023-06-09T12:34:56.789Z");
    assertEquals(expectedInstant, instant);
  }

  @Test
  public void testByteArraySerializer() throws IOException {
    byte[] byteArray = "Hello, World!".getBytes();

    String jsonString = DatabindCodec.mapper().writeValueAsString(byteArray);

    String expectedBase64String = Base64.getEncoder().withoutPadding().encodeToString(byteArray);
    assertEquals("\"" + expectedBase64String + "\"", jsonString);
  }

  @Test
  public void testByteArrayDeserializer() throws IOException {
    String jsonString = "\"SGVsbG8sIFdvcmxkIQ\"";

    byte[] byteArray = DatabindCodec.mapper().readValue(jsonString, byte[].class);

    byte[] expectedByteArray = Base64.getDecoder().decode("SGVsbG8sIFdvcmxkIQ");
    assertArrayEquals(expectedByteArray, byteArray);
  }

  @Test
  public void testBufferSerializer() throws IOException {

    Buffer buffer = Buffer.buffer("Hello, World!");

    String jsonString = DatabindCodec.mapper().writeValueAsString(buffer);

    assertEquals("\"SGVsbG8sIFdvcmxkIQ\"", jsonString);
  }

  @Test
  public void testBufferDeserializer() throws IOException {
    String jsonString = "\"SGVsbG8sIFdvcmxkIQ\"";

    Buffer buffer = DatabindCodec.mapper().readValue(jsonString, Buffer.class);

    Buffer expectedBuffer = Buffer.buffer("Hello, World!");
    assertEquals(expectedBuffer, buffer);
  }

  private static class Pojo {
    @JsonProperty
    String value;
    @JsonProperty
    Instant instant;
    @JsonProperty
    byte[] bytes;
  }

  @Test
  public void testPrettyPrinting() {
    JsonObject jsonObject = new JsonObject()
      .put("key1", "value1")
      .put("key2", "value2")
      .put("key3", "value3");

    String compact = Json.encode(jsonObject);
    String pretty = Json.encodePrettily(jsonObject);

    assertFalse(compact.equals(pretty));

    assertEquals(jsonObject, Json.decodeValue(pretty));
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
}
