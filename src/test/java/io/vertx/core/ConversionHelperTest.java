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

package io.vertx.core;

import io.netty.util.AsciiString;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ConversionHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConversionHelperTest {

  @Test
  public void testToJsonObject() {
    Map<String, Object> map = new HashMap<>();
    map.put("string", "the_string");
    map.put("integer", 4);
    map.put("boolean", true);
    map.put("charsequence", new AsciiString("the_charsequence"));
    map.put("biginteger", new BigInteger("1234567"));
    map.put("binary", Buffer.buffer("hello"));
    map.put("object", Collections.singletonMap("nested", 4));
    map.put("array", Arrays.asList(1, 2, 3));
    JsonObject json = (JsonObject) ConversionHelper.toObject(map);
    assertEquals(8, json.size());
    assertEquals("the_string", json.getString("string"));
    assertEquals(4, (int)json.getInteger("integer"));
    assertEquals(true, json.getBoolean("boolean"));
    assertEquals("the_charsequence", json.getString("charsequence"));
    assertEquals(1234567, (int)json.getInteger("biginteger"));
    assertEquals("hello", new String(json.getBinary("binary")));
    assertEquals(new JsonObject().put("nested", 4), json.getJsonObject("object"));
    assertEquals(new JsonArray().add(1).add(2).add(3), json.getJsonArray("array"));
  }

  @Test
  public void testToJsonArray() {
    List<Object> list = new ArrayList<>();
    list.add("the_string");
    list.add(4);
    list.add(true);
    list.add(new AsciiString("the_charsequence"));
    list.add(new BigInteger("1234567"));
    list.add(Buffer.buffer("hello"));
    list.add(Collections.singletonMap("nested", 4));
    list.add(Arrays.asList(1, 2, 3));
    JsonArray json = (JsonArray) ConversionHelper.toObject(list);
    assertEquals(8, json.size());
    assertEquals("the_string", json.getString(0));
    assertEquals(4, (int)json.getInteger(1));
    assertEquals(true, json.getBoolean(2));
    assertEquals("the_charsequence", json.getString(3));
    assertEquals(1234567, (int)json.getInteger(4));
    assertEquals("hello", new String(json.getBinary(5)));
    assertEquals(new JsonObject().put("nested", 4), json.getJsonObject(6));
    assertEquals(new JsonArray().add(1).add(2).add(3), json.getJsonArray(7));
  }

  @Test
  public void testToString() {
    assertEquals("the_string", ConversionHelper.toObject(new AsciiString("the_string")));
  }

  @Test
  public void testToObject() {
    Object o = new Object();
    assertEquals(o, ConversionHelper.toObject(o));
  }

  @Test
  public void testFromJsonObject() {
    JsonObject object = new JsonObject();
    object.put("string", "the_string");
    object.put("integer", 4);
    object.put("boolean", true);
    object.put("binary", "hello".getBytes());
    object.put("object", new JsonObject().put("nested", 4));
    object.put("array", new JsonArray().add(1).add(2).add(3));
    Map<String, Object> map = ConversionHelper.fromObject(object);
    assertEquals(6, map.size());
    assertEquals("the_string", map.get("string"));
    assertEquals(4, map.get("integer"));
    assertEquals(true, map.get("boolean"));
    assertEquals("hello", new String(Base64.getDecoder().decode((String)map.get("binary"))));
    assertEquals(Collections.singletonMap("nested", 4), map.get("object"));
    assertEquals(Arrays.asList(1, 2, 3), map.get("array"));
  }

  @Test
  public void testFromJsonArray() {
    JsonArray object = new JsonArray();
    object.add("the_string");
    object.add(4);
    object.add(true);
    object.add("hello".getBytes());
    object.add(new JsonObject().put("nested", 4));
    object.add(new JsonArray().add(1).add(2).add(3));
    List<Object> map = ConversionHelper.fromObject(object);
    assertEquals(6, map.size());
    assertEquals("the_string", map.get(0));
    assertEquals(4, map.get(1));
    assertEquals(true, map.get(2));
    assertEquals("hello", new String(Base64.getDecoder().decode((String)map.get(3))));
    assertEquals(Collections.singletonMap("nested", 4), map.get(4));
    assertEquals(Arrays.asList(1, 2, 3), map.get(5));
  }

  /**
   * Confirm that when we convert to map/list form we do so recursively.
   */
  @Test
  public void testWrapObject() {
    // Create a JsonObject with nested JsonObject and JsonArray values
    JsonObject obj = new JsonObject()
        .put("nestedObj", new JsonObject().put("key", "value"))
        .put("nestedList", new JsonArray().add(new JsonObject().put("key", "value")));

    // Get the wrapped form and confirm that it acted recursively
    Map<String, Object> wrapped = ConversionHelper.fromObject(obj);
    assertTrue(wrapped.get("nestedObj") instanceof Map);
    List<Object> theList = (List<Object>) wrapped.get("nestedList");
    assertTrue(theList.get(0) instanceof Map);
  }
}
