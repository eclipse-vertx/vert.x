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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.time.Instant;
import java.util.Base64;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class JacksonDatabindTestBase extends VertxTestBase {

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
  public void testJsonArrayDeserializer() throws Exception {
    String jsonArrayString = "[1, 2, 3]";
    JsonArray jsonArray = readValue(jsonArrayString, JsonArray.class);
    assertEquals(3, jsonArray.size());
    assertEquals(new JsonArray().add(1).add(2).add(3), jsonArray);

  }

  @Test
  public void testJsonObjectDeserializer() throws Exception {
    String jsonObjectString = "{\"key1\": \"value1\", \"key2\": \"value2\", \"key3\": \"value3\"}";
    JsonObject jsonObject = readValue(jsonObjectString, JsonObject.class);
    assertEquals("value1", jsonObject.getString("key1"));
    assertEquals("value2", jsonObject.getString("key2"));
  }

  protected abstract String writeValueAsString(Object o) throws Exception;
  protected abstract <T> T readValue(String content, Class<T> valueType) throws Exception;

  @Test
  public void testJsonObjectSerializer() throws Exception {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("key1", "value1");
    jsonObject.put("key2", "value2");
    jsonObject.put("key3", "value3");

    String jsonString = writeValueAsString(jsonObject);

    assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}", jsonString);
  }

  @Test
  public void testJsonArraySerializer() throws Exception {


    JsonArray jsonArray = new JsonArray();
    jsonArray.add("value1");
    jsonArray.add("value2");
    jsonArray.add("value3");

    String jsonString = writeValueAsString(jsonArray);

    assertEquals("[\"value1\",\"value2\",\"value3\"]", jsonString);
  }

  @Test
  public void testInstantSerializer() throws Exception {

    Instant instant = Instant.parse("2023-06-09T12:34:56.789Z");

    String jsonString = writeValueAsString(instant);

    assertEquals("\"2023-06-09T12:34:56.789Z\"", jsonString);
  }

  @Test
  public void testInstantDeserializer() throws Exception {

    String jsonString = "\"2023-06-09T12:34:56.789Z\"";

    Instant instant = readValue(jsonString, Instant.class);

    Instant expectedInstant = Instant.parse("2023-06-09T12:34:56.789Z");
    assertEquals(expectedInstant, instant);
  }

  @Test
  public void testByteArraySerializer() throws Exception {
    byte[] byteArray = "Hello, World!".getBytes();

    String jsonString = writeValueAsString(byteArray);

    String expectedBase64String = Base64.getEncoder().withoutPadding().encodeToString(byteArray);
    assertEquals("\"" + expectedBase64String + "\"", jsonString);
  }

  @Test
  public void testByteArrayDeserializer() throws Exception {
    String jsonString = "\"SGVsbG8sIFdvcmxkIQ\"";

    byte[] byteArray = readValue(jsonString, byte[].class);

    byte[] expectedByteArray = Base64.getDecoder().decode("SGVsbG8sIFdvcmxkIQ");
    assertArrayEquals(expectedByteArray, byteArray);
  }

  @Test
  public void testBufferSerializer() throws Exception {

    Buffer buffer = Buffer.buffer("Hello, World!");

    String jsonString = writeValueAsString(buffer);

    assertEquals("\"SGVsbG8sIFdvcmxkIQ\"", jsonString);
  }

  @Test
  public void testBufferDeserializer() throws Exception {
    String jsonString = "\"SGVsbG8sIFdvcmxkIQ\"";

    Buffer buffer = readValue(jsonString, Buffer.class);

    Buffer expectedBuffer = Buffer.buffer("Hello, World!");
    assertEquals(expectedBuffer, buffer);
  }

  public static class Pojo {
    @JsonProperty
    public String value;
    @JsonProperty
    public Instant instant;
    @JsonProperty
    public byte[] bytes;
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
}
