/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.junit.Assert.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArrayTest {

  private JsonArray jsonArray;

  @Before
  public void setUp() {
    jsonArray = new JsonArray();
  }

  @Test
  public void testGetInteger() {
    jsonArray.add(123);
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(0));
    try {
      jsonArray.getInteger(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getInteger(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    // Different number types
    jsonArray.add(123l);
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(1));
    jsonArray.add(123f);
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(2));
    jsonArray.add(123d);
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(3));
    jsonArray.add("foo");
    try {
      jsonArray.getInteger(4);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getInteger(5));
  }

  @Test
  public void testGetLong() {
    jsonArray.add(123l);
    assertEquals(Long.valueOf(123l), jsonArray.getLong(0));
    try {
      jsonArray.getLong(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getLong(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    // Different number types
    jsonArray.add(123);
    assertEquals(Long.valueOf(123l), jsonArray.getLong(1));
    jsonArray.add(123f);
    assertEquals(Long.valueOf(123l), jsonArray.getLong(2));
    jsonArray.add(123d);
    assertEquals(Long.valueOf(123l), jsonArray.getLong(3));
    jsonArray.add("foo");
    try {
      jsonArray.getLong(4);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getLong(5));
  }

  @Test
  public void testGetFloat() {
    jsonArray.add(123f);
    assertEquals(Float.valueOf(123f), jsonArray.getFloat(0));
    try {
      jsonArray.getFloat(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getFloat(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    // Different number types
    jsonArray.add(123);
    assertEquals(Float.valueOf(123f), jsonArray.getFloat(1));
    jsonArray.add(123);
    assertEquals(Float.valueOf(123f), jsonArray.getFloat(2));
    jsonArray.add(123d);
    assertEquals(Float.valueOf(123f), jsonArray.getFloat(3));
    jsonArray.add("foo");
    try {
      jsonArray.getFloat(4);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getFloat(5));
  }

  @Test
  public void testGetDouble() {
    jsonArray.add(123d);
    assertEquals(Double.valueOf(123d), jsonArray.getDouble(0));
    try {
      jsonArray.getDouble(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getDouble(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    // Different number types
    jsonArray.add(123);
    assertEquals(Double.valueOf(123d), jsonArray.getDouble(1));
    jsonArray.add(123);
    assertEquals(Double.valueOf(123d), jsonArray.getDouble(2));
    jsonArray.add(123d);
    assertEquals(Double.valueOf(123d), jsonArray.getDouble(3));
    jsonArray.add("foo");
    try {
      jsonArray.getDouble(4);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getDouble(5));
  }

  @Test
  public void testGetString() {
    jsonArray.add("foo");
    assertEquals("foo", jsonArray.getString(0));
    try {
      jsonArray.getString(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getString(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    jsonArray.add(123);
    try {
      jsonArray.getString(1);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getString(2));
  }

  @Test
  public void testGetBoolean() {
    jsonArray.add(true);
    assertEquals(true, jsonArray.getBoolean(0));
    jsonArray.add(false);
    assertEquals(false, jsonArray.getBoolean(1));
    try {
      jsonArray.getBoolean(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getBoolean(2);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    jsonArray.add(123);
    try {
      jsonArray.getBoolean(2);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getBoolean(3));
  }

  @Test
  public void testGetBinary() {
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    assertArrayEquals(bytes, jsonArray.getBinary(0));
    assertEquals(Base64.getEncoder().encodeToString(bytes), jsonArray.getValue(0));
    assertArrayEquals(bytes, Base64.getDecoder().decode(jsonArray.getString(0)));
    try {
      jsonArray.getBinary(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getBinary(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    jsonArray.add(123);
    try {
      jsonArray.getBinary(1);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getBinary(2));
  }

  @Test
  public void testGetInstant() {
    Instant now = Instant.now();
    jsonArray.add(now);
    assertEquals(now, jsonArray.getInstant(0));
    assertEquals(now.toString(), jsonArray.getValue(0));
    assertEquals(now, Instant.from(ISO_INSTANT.parse(jsonArray.getString(0))));
    try {
      jsonArray.getInstant(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getValue(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getInstant(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getValue(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    jsonArray.add(123);
    try {
      jsonArray.getInstant(1);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getInstant(2));
    assertNull(jsonArray.getValue(2));
  }

  @Test
  public void testGetJsonObject() {
    JsonObject obj = new JsonObject().put("foo", "bar");
    jsonArray.add(obj);
    assertEquals(obj, jsonArray.getJsonObject(0));
    try {
      jsonArray.getJsonObject(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getJsonObject(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    jsonArray.add(123);
    try {
      jsonArray.getJsonObject(1);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getJsonObject(2));
  }

  @Test
  public void testGetJsonArray() {
    JsonArray arr = new JsonArray().add("foo");
    jsonArray.add(arr);
    assertEquals(arr, jsonArray.getJsonArray(0));
    try {
      jsonArray.getJsonArray(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getJsonArray(1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    jsonArray.add(123);
    try {
      jsonArray.getJsonArray(1);
      fail();
    } catch (ClassCastException e) {
      // OK
    }
    jsonArray.addNull();
    assertNull(jsonArray.getJsonArray(2));
  }

  @Test
  public void testGetValue() {
    jsonArray.add(123);
    assertEquals(123, jsonArray.getValue(0));
    jsonArray.add(123l);
    assertEquals(123l, jsonArray.getValue(1));
    jsonArray.add(123f);
    assertEquals(123f, jsonArray.getValue(2));
    jsonArray.add(123d);
    assertEquals(123d, jsonArray.getValue(3));
    jsonArray.add(false);
    assertEquals(false, jsonArray.getValue(4));
    jsonArray.add(true);
    assertEquals(true, jsonArray.getValue(5));
    jsonArray.add("bar");
    assertEquals("bar", jsonArray.getValue(6));
    JsonObject obj = new JsonObject().put("blah", "wibble");
    jsonArray.add(obj);
    assertEquals(obj, jsonArray.getValue(7));
    JsonArray arr = new JsonArray().add("blah").add("wibble");
    jsonArray.add(arr);
    assertEquals(arr, jsonArray.getValue(8));
    byte[] bytes = TestUtils.randomByteArray(100);
    jsonArray.add(bytes);
    assertEquals(Base64.getEncoder().encodeToString(bytes), jsonArray.getValue(9));
    Instant now = Instant.now();
    jsonArray.add(now);
    assertEquals(now, jsonArray.getInstant(10));
    assertEquals(now.toString(), jsonArray.getValue(10));
    jsonArray.addNull();
    assertNull(jsonArray.getValue(11));
    try {
      jsonArray.getValue(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getValue(12);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    // JsonObject with inner Map
    List<Object> list = new ArrayList<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("blah", "wibble");
    list.add(innerMap);
    jsonArray = new JsonArray(list);
    obj = (JsonObject)jsonArray.getValue(0);
    assertEquals("wibble", obj.getString("blah"));
    // JsonObject with inner List
    list = new ArrayList<>();
    List<Object> innerList = new ArrayList<>();
    innerList.add("blah");
    list.add(innerList);
    jsonArray = new JsonArray(list);
    arr = (JsonArray)jsonArray.getValue(0);
    assertEquals("blah", arr.getString(0));
  }

  enum SomeEnum {
    FOO, BAR
  }

  @Test
  public void testAddEnum() {
    assertSame(jsonArray, jsonArray.add(JsonObjectTest.SomeEnum.FOO));
    assertEquals(JsonObjectTest.SomeEnum.FOO.toString(), jsonArray.getString(0));
    jsonArray.add((JsonObjectTest.SomeEnum)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddString() {
    assertSame(jsonArray, jsonArray.add("foo"));
    assertEquals("foo", jsonArray.getString(0));
    jsonArray.add((String)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddCharSequence() {
    assertSame(jsonArray, jsonArray.add(new StringBuilder("bar")));
    assertEquals("bar", jsonArray.getString(0));
    jsonArray.add((CharSequence) null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddInteger() {
    assertSame(jsonArray, jsonArray.add(123));
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(0));
    jsonArray.add((Integer)null);
  }

  @Test
  public void testAddLong() {
    assertSame(jsonArray, jsonArray.add(123l));
    assertEquals(Long.valueOf(123l), jsonArray.getLong(0));
    jsonArray.add((Long)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddFloat() {
    assertSame(jsonArray, jsonArray.add(123f));
    assertEquals(Float.valueOf(123f), jsonArray.getFloat(0));
    jsonArray.add((Float)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddDouble() {
    assertSame(jsonArray, jsonArray.add(123d));
    assertEquals(Double.valueOf(123d), jsonArray.getDouble(0));
    jsonArray.add((Double)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddBoolean() {
    assertSame(jsonArray, jsonArray.add(true));
    assertEquals(true, jsonArray.getBoolean(0));
    jsonArray.add(false);
    assertEquals(false, jsonArray.getBoolean(1));
    jsonArray.add((Boolean)null);
    assertNull(jsonArray.getValue(2));
    assertEquals(3, jsonArray.size());
  }

  @Test
  public void testAddJsonObject() {
    JsonObject obj = new JsonObject().put("foo", "bar");
    assertSame(jsonArray, jsonArray.add(obj));
    assertEquals(obj, jsonArray.getJsonObject(0));
    jsonArray.add((JsonObject)null);
    assertNull(jsonArray.getJsonObject(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddJsonArray() {
    JsonArray arr = new JsonArray().add("foo");
    assertSame(jsonArray, jsonArray.add(arr));
    assertEquals(arr, jsonArray.getJsonArray(0));
    jsonArray.add((JsonArray)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddBinary() {
    byte[] bytes = TestUtils.randomByteArray(10);
    assertSame(jsonArray, jsonArray.add(bytes));
    assertArrayEquals(bytes, jsonArray.getBinary(0));
    assertEquals(Base64.getEncoder().encodeToString(bytes), jsonArray.getValue(0));
    jsonArray.add((byte[])null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddInstant() {
    Instant now = Instant.now();
    assertSame(jsonArray, jsonArray.add(now));
    assertEquals(now, jsonArray.getInstant(0));
    assertEquals(now.toString(), jsonArray.getValue(0));
    jsonArray.add((Instant)null);
    assertNull(jsonArray.getValue(1));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testAddObject() {
    jsonArray.add((Object)"bar");
    jsonArray.add((Object)(Integer.valueOf(123)));
    jsonArray.add((Object)(Long.valueOf(123l)));
    jsonArray.add((Object)(Float.valueOf(1.23f)));
    jsonArray.add((Object)(Double.valueOf(1.23d)));
    jsonArray.add((Object) true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add((Object)(bytes));
    Instant now = Instant.now();
    jsonArray.add(now);
    JsonObject obj = new JsonObject().put("foo", "blah");
    JsonArray arr = new JsonArray().add("quux");
    jsonArray.add((Object)obj);
    jsonArray.add((Object)arr);
    assertEquals("bar", jsonArray.getString(0));
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(1));
    assertEquals(Long.valueOf(123l), jsonArray.getLong(2));
    assertEquals(Float.valueOf(1.23f), jsonArray.getFloat(3));
    assertEquals(Double.valueOf(1.23d), jsonArray.getDouble(4));
    assertEquals(true, jsonArray.getBoolean(5));
    assertArrayEquals(bytes, jsonArray.getBinary(6));
    assertEquals(Base64.getEncoder().encodeToString(bytes), jsonArray.getValue(6));
    assertEquals(now, jsonArray.getInstant(7));
    assertEquals(now.toString(), jsonArray.getValue(7));
    assertEquals(obj, jsonArray.getJsonObject(8));
    assertEquals(arr, jsonArray.getJsonArray(9));
    try {
      jsonArray.add(new SomeClass());
      fail();
    } catch (IllegalStateException e) {
      // OK
    }
    try {
      jsonArray.add(new BigDecimal(123));
      fail();
    } catch (IllegalStateException e) {
      // OK
    }
    try {
      jsonArray.add(new Date());
      fail();
    } catch (IllegalStateException e) {
      // OK
    }

  }

  @Test
  public void testAddAllJsonArray() {
    jsonArray.add("bar");
    JsonArray arr = new JsonArray().add("foo").add(48);
    assertSame(jsonArray, jsonArray.addAll(arr));
    assertEquals(arr.getString(0), jsonArray.getString(1));
    assertEquals(arr.getInteger(1), jsonArray.getInteger(2));
    jsonArray.add((JsonArray)null);
    assertNull(jsonArray.getValue(3));
    assertEquals(4, jsonArray.size());
  }

  @Test
  public void testAddNull() {
    assertSame(jsonArray, jsonArray.addNull());
    assertEquals(null, jsonArray.getString(0));
    assertTrue(jsonArray.hasNull(0));
  }

  @Test
  public void testHasNull() {
    jsonArray.addNull();
    jsonArray.add("foo");
    assertEquals(null, jsonArray.getString(0));
    assertTrue(jsonArray.hasNull(0));
    assertFalse(jsonArray.hasNull(1));
  }

  @Test
  public void testContains() {
    jsonArray.add("wibble");
    jsonArray.add(true);
    jsonArray.add(123);
    JsonObject obj = new JsonObject();
    JsonArray arr = new JsonArray();
    jsonArray.add(obj);
    jsonArray.add(arr);
    assertFalse(jsonArray.contains("eek"));
    assertFalse(jsonArray.contains(false));
    assertFalse(jsonArray.contains(321));
    assertFalse(jsonArray.contains(new JsonObject().put("blah", "flib")));
    assertFalse(jsonArray.contains(new JsonArray().add("oob")));
    assertTrue(jsonArray.contains("wibble"));
    assertTrue(jsonArray.contains(true));
    assertTrue(jsonArray.contains(123));
    assertTrue(jsonArray.contains(obj));
    assertTrue(jsonArray.contains(arr));
  }

  @Test
  public void testRemoveByObject() {
    jsonArray.add("wibble");
    jsonArray.add(true);
    jsonArray.add(123);
    assertEquals(3, jsonArray.size());
    assertTrue(jsonArray.remove("wibble"));
    assertEquals(2, jsonArray.size());
    assertFalse(jsonArray.remove("notthere"));
    assertTrue(jsonArray.remove(true));
    assertTrue(jsonArray.remove(Integer.valueOf(123)));
    assertTrue(jsonArray.isEmpty());
  }

  @Test
  public void testRemoveByPos() {
    jsonArray.add("wibble");
    jsonArray.add(true);
    jsonArray.add(123);
    assertEquals(3, jsonArray.size());
    assertEquals("wibble", jsonArray.remove(0));
    assertEquals(2, jsonArray.size());
    assertEquals(123, jsonArray.remove(1));
    assertEquals(1, jsonArray.size());
    assertEquals(true, jsonArray.remove(0));
    assertTrue(jsonArray.isEmpty());
  }

  @Test
  public void testSize() {
    jsonArray.add("wibble");
    jsonArray.add(true);
    jsonArray.add(123);
    assertEquals(3, jsonArray.size());
  }

  @Test
  public void testClear() {
    jsonArray.add("wibble");
    jsonArray.add(true);
    jsonArray.add(123);
    assertEquals(3, jsonArray.size());
    assertEquals(jsonArray, jsonArray.clear());
    assertEquals(0, jsonArray.size());
    assertTrue(jsonArray.isEmpty());
  }

  @Test
  public void testIterator() {
    jsonArray.add("foo");
    jsonArray.add(123);
    JsonObject obj = new JsonObject().put("foo", "bar");
    jsonArray.add(obj);
    Iterator<Object> iter = jsonArray.iterator();
    assertTrue(iter.hasNext());
    Object entry = iter.next();
    assertEquals("foo", entry);
    assertTrue(iter.hasNext());
    entry = iter.next();
    assertEquals(123, entry);
    assertTrue(iter.hasNext());
    entry = iter.next();
    assertEquals(obj, entry);
    assertFalse(iter.hasNext());
    iter.remove();
    assertFalse(jsonArray.contains(obj));
    assertEquals(2, jsonArray.size());
  }

  @Test
  public void testStream() {
    jsonArray.add("foo");
    jsonArray.add(123);
    JsonObject obj = new JsonObject().put("foo", "bar");
    jsonArray.add(obj);
    List<Object> list = jsonArray.stream().collect(Collectors.toList());
    Iterator<Object> iter = list.iterator();
    assertTrue(iter.hasNext());
    Object entry = iter.next();
    assertEquals("foo", entry);
    assertTrue(iter.hasNext());
    entry = iter.next();
    assertEquals(123, entry);
    assertTrue(iter.hasNext());
    entry = iter.next();
    assertEquals(obj, entry);
    assertFalse(iter.hasNext());
  }

  @Test
  public void testCopy() {
    jsonArray.add("foo");
    jsonArray.add(123);
    JsonObject obj = new JsonObject().put("foo", "bar");
    jsonArray.add(obj);
    jsonArray.add(new StringBuilder("eeek"));
    JsonArray copy = jsonArray.copy();
    assertEquals("eeek", copy.getString(3));
    assertNotSame(jsonArray, copy);
    assertEquals(jsonArray, copy);
    assertEquals(4, copy.size());
    assertEquals("foo", copy.getString(0));
    assertEquals(Integer.valueOf(123), copy.getInteger(1));
    assertEquals(obj, copy.getJsonObject(2));
    assertNotSame(obj, copy.getJsonObject(2));
    copy.add("foo");
    assertEquals(4, jsonArray.size());
    jsonArray.add("bar");
    assertEquals(5, copy.size());
  }

  @Test
  public void testInvalidValsOnCopy() {
    List<Object> invalid = new ArrayList<>();
    invalid.add(new SomeClass());
    JsonArray arr = new JsonArray(invalid);
    try {
      arr.copy();
      fail();
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testInvalidValsOnCopy2() {
    List<Object> invalid = new ArrayList<>();
    List<Object> invalid2 = new ArrayList<>();
    invalid2.add(new SomeClass());
    invalid.add(invalid2);
    JsonArray arr = new JsonArray(invalid);
    try {
      arr.copy();
      fail();
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testInvalidValsOnCopy3() {
    List<Object> invalid = new ArrayList<>();
    Map<String, Object> invalid2 = new HashMap<>();
    invalid2.put("foo", new SomeClass());
    invalid.add(invalid2);
    JsonArray arr = new JsonArray(invalid);
    try {
      arr.copy();
      fail();
    } catch (IllegalStateException e) {
      // OK
    }
  }

  class SomeClass {
  }

  @Test
  public void testToString() {
    jsonArray.add("foo").add(123);
    assertEquals(jsonArray.encode(), jsonArray.toString());
  }

  @Test
  public void testGetList() {
    JsonObject obj = new JsonObject().put("quux", "wibble");
    jsonArray.add("foo").add(123).add(obj);
    List<Object> list = jsonArray.getList();
    list.remove("foo");
    assertFalse(jsonArray.contains("foo"));
    list.add("floob");
    assertTrue(jsonArray.contains("floob"));
    assertSame(obj, list.get(1));
    obj.remove("quux");
  }

  @Test
  public void testCreateFromList() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    list.add(123);
    JsonArray arr = new JsonArray(list);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertSame(list, arr.getList());
  }

  @Test
  public void testCreateFromListCharSequence() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    list.add(123);
    list.add(new StringBuilder("eek"));
    JsonArray arr = new JsonArray(list);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertEquals("eek", arr.getString(2));
    assertSame(list, arr.getList());
  }

  @Test
  public void testCreateFromListNestedJsonObject() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    list.add(123);
    JsonObject obj = new JsonObject().put("blah", "wibble");
    list.add(obj);
    JsonArray arr = new JsonArray(list);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertSame(list, arr.getList());
    assertSame(obj, arr.getJsonObject(2));
  }

  @Test
  public void testCreateFromListNestedMap() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    list.add(123);
    Map<String, Object> map = new HashMap<>();
    map.put("blah", "wibble");
    list.add(map);
    JsonArray arr = new JsonArray(list);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertSame(list, arr.getList());
    JsonObject obj = arr.getJsonObject(2);
    assertSame(map, obj.getMap());
  }

  @Test
  public void testCreateFromListNestedJsonArray() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    list.add(123);
    JsonArray arr2 = new JsonArray().add("blah").add("wibble");
    list.add(arr2);
    JsonArray arr = new JsonArray(list);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertSame(list, arr.getList());
    assertSame(arr2, arr.getJsonArray(2));
  }

  @Test
  public void testCreateFromListNestedList() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    list.add(123);
    List<Object> list2 = new ArrayList<>();
    list2.add("blah");
    list2.add("wibble");
    list.add(list2);
    JsonArray arr = new JsonArray(list);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertSame(list, arr.getList());
    JsonArray arr2 = arr.getJsonArray(2);
    assertSame(list2, arr2.getList());
  }

  @Test
  public void testCreateFromBuffer() {
    JsonArray excepted = new JsonArray();
    excepted.add("foobar");
    excepted.add(123);
    Buffer buf = Buffer.buffer(excepted.encode());
    assertEquals(excepted, new JsonArray(buf));
  }

  @Test
  public void testClusterSerializable() {
    jsonArray.add("foo").add(123);
    Buffer buff = Buffer.buffer();
    jsonArray.writeToBuffer(buff);
    JsonArray deserialized = new JsonArray();
    deserialized.readFromBuffer(0, buff);
    assertEquals(jsonArray, deserialized);
  }

  @Test
  public void testJsonArrayEquality() {
    JsonObject obj = new JsonObject(Collections.singletonMap("abc", Collections.singletonList(3)));
    assertEquals(obj, new JsonObject(Collections.singletonMap("abc", Collections.singletonList(3))));
    assertEquals(obj, new JsonObject(Collections.singletonMap("abc", Collections.singletonList(3L))));
    assertEquals(obj, new JsonObject(Collections.singletonMap("abc", new JsonArray().add(3))));
    assertEquals(obj, new JsonObject(Collections.singletonMap("abc", new JsonArray().add(3L))));
    assertNotEquals(obj, new JsonObject(Collections.singletonMap("abc", Collections.singletonList(4))));
    assertNotEquals(obj, new JsonObject(Collections.singletonMap("abc", new JsonArray().add(4))));
    JsonArray array = new JsonArray(Collections.singletonList(Collections.singletonList(3)));
    assertEquals(array, new JsonArray(Collections.singletonList(Collections.singletonList(3))));
    assertEquals(array, new JsonArray(Collections.singletonList(Collections.singletonList(3L))));
    assertEquals(array, new JsonArray(Collections.singletonList(new JsonArray().add(3))));
    assertEquals(array, new JsonArray(Collections.singletonList(new JsonArray().add(3L))));
    assertNotEquals(array, new JsonArray(Collections.singletonList(Collections.singletonList(4))));
    assertNotEquals(array, new JsonArray(Collections.singletonList(new JsonArray().add(4))));
  }

  @Test
  public void testStreamCorrectTypes() throws Exception {
    String json = "{\"object1\": [{\"object2\": 12}]}";
    JsonObject object = new JsonObject(json);
    testStreamCorrectTypes(object.copy());
    testStreamCorrectTypes(object);
  }

  @Test
  public void testRemoveMethodReturnedObject() {
    JsonArray obj = new JsonArray();
    obj.add("bar")
        .add(new JsonObject().put("name", "vert.x").put("count", 2))
        .add(new JsonArray().add(1.0).add(2.0));

    Object removed = obj.remove(0);
    assertTrue(removed instanceof String);

    removed = obj.remove(0);
    assertTrue(removed instanceof JsonObject);
    assertEquals(((JsonObject) removed).getString("name"), "vert.x");

    removed = obj.remove(0);
    assertTrue(removed instanceof JsonArray);
    assertEquals(((JsonArray) removed).getDouble(0), 1.0, 0.0);
  }

  private void testStreamCorrectTypes(JsonObject object) {
    object.getJsonArray("object1").stream().forEach(innerMap -> {
      assertTrue("Expecting JsonObject, found: " + innerMap.getClass().getCanonicalName(), innerMap instanceof JsonObject);
    });
  }

  @Test
  public void testInvalidConstruction() {
    try {
      new JsonArray((String) null);
      fail();
    } catch (NullPointerException ignore) {
    }
    try {
      new JsonArray((Buffer) null);
      fail();
    } catch (NullPointerException ignore) {
    }
    try {
      new JsonArray((List) null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }
}
