/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.it;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

/**
 * This test verifies that, even when the user registers a Jackson Databind module that modifies container types,
 * such as the Scala module, Vert.x can properly deserialize {@link JsonObject}, {@link JsonArray},
 * or a POJO with fields having one these types.
 */
public class CustomObjectMapperTest {

  private static final MyBeanDeserializerModifier DESERIALIZER_MODIFIER = new MyBeanDeserializerModifier();

  @BeforeClass
  public static void beforeClass() {
    DatabindCodec.mapper().registerModule(new SimpleModule().setDeserializerModifier(DESERIALIZER_MODIFIER));
  }

  @Before
  public void setUp() throws Exception {
    DESERIALIZER_MODIFIER.reset();
  }

  @Test
  public void testDecodeJsonArray() {
    String jsonArrayString = "[[1, 2, 3], 2, {\"key1\": \"value1\", \"key2\": \"value2\", \"key3\": \"value3\"}]";

    JsonArray jsonArray = (JsonArray) Json.decodeValue(jsonArrayString);

    assertEquals(3, jsonArray.size());
    JsonArray expected = new JsonArray()
      .add(new JsonArray().add(1).add(2).add(3))
      .add(2)
      .add(new JsonObject().put("key1", "value1").put("key2", "value2").put("key3", "value3"));
    assertEquals(expected, jsonArray);
  }

  @Test
  public void testDecodeJsonObject() {
    String jsonObjectString = "{\"key1\": \"value1\", \"key2\": [1, 2, 3], \"key3\": \"value3\"}";

    JsonObject jsonObject = (JsonObject) Json.decodeValue(jsonObjectString);

    assertEquals("value1", jsonObject.getString("key1"));
    assertEquals(new JsonArray().add(1).add(2).add(3), jsonObject.getJsonArray("key2"));
  }

  @Test
  public void testDecodePojo() {
    String jsonString = "{" +
                        "\"str\": \"foo\"," +
                        "\"when\": \"2011-12-03T10:15:30Z\"," +
                        "\"nil\": null," +
                        "\"obj\": {\"key1\": \"value1\", \"key2\": [1, false, 3], \"key3\": \"value3\"}," +
                        "\"arr\": [[1, 2, 3], 2, {\"key1\": \"value1\", \"key2\": \"value2\", \"key3\": \"value3\"}]" +
                        "}";

    PojoWithEmbeddedJson pojo = Json.decodeValue(jsonString, PojoWithEmbeddedJson.class);

    assertEquals("foo", pojo.str);
    assertEquals(LocalDateTime.of(2011, 12, 3, 10, 15, 30).toInstant(ZoneOffset.UTC), pojo.when);
    assertNull("foo", pojo.nil);
    JsonArray expectedArr = new JsonArray()
      .add(new JsonArray().add(1).add(2).add(3))
      .add(2)
      .add(new JsonObject().put("key1", "value1").put("key2", "value2").put("key3", "value3"));
    assertEquals(expectedArr, pojo.arr);
    JsonObject expectedObj = new JsonObject()
      .put("key1", "value1")
      .put("key2", new JsonArray().add(1).add(false).add(3))
      .put("key3", "value3");
    assertEquals(expectedObj, pojo.obj);


    assertTrue(DESERIALIZER_MODIFIER.collectionDeserializerModified);
    assertTrue(DESERIALIZER_MODIFIER.mapDeserializerModified);
  }

  @SuppressWarnings("unused")
  public static class PojoWithEmbeddedJson {

    private String str;
    private Instant when;
    private Object nil;
    private JsonObject obj;
    private JsonArray arr;

    public String getStr() {
      return str;
    }

    public void setStr(String str) {
      this.str = str;
    }

    public Instant getWhen() {
      return when;
    }

    public void setWhen(Instant when) {
      this.when = when;
    }

    public Object getNil() {
      return nil;
    }

    public void setNil(Object nil) {
      this.nil = nil;
    }

    public JsonObject getObj() {
      return obj;
    }

    public void setObj(JsonObject obj) {
      this.obj = obj;
    }

    public JsonArray getArr() {
      return arr;
    }

    public void setArr(JsonArray arr) {
      this.arr = arr;
    }
  }

  private static class MyBeanDeserializerModifier extends BeanDeserializerModifier {

    boolean collectionDeserializerModified = true;
    boolean mapDeserializerModified = true;

    @Override
    public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
      collectionDeserializerModified = true;
      return new CustomDeserializer();
    }

    @Override
    public JsonDeserializer<?> modifyMapDeserializer(DeserializationConfig config, MapType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
      mapDeserializerModified = true;
      return new CustomDeserializer();
    }

    public void reset() {
      collectionDeserializerModified = mapDeserializerModified = false;
    }
  }

  private static class CustomDeserializer extends JsonDeserializer<Object> {
    @Override
    public Object deserialize(JsonParser parser, DeserializationContext ctx) {
      throw new AssertionError("Should not be used");
    }
  }
}
