/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Utils;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    assertTrue(TestUtils.byteArraysEqual(bytes, jsonArray.getBinary(0)));
    assertTrue(TestUtils.byteArraysEqual(bytes, Base64.getDecoder().decode(jsonArray.getString(0))));
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
    assertTrue(TestUtils.byteArraysEqual(bytes, Base64.getDecoder().decode((String) jsonArray.getValue(9))));
    jsonArray.addNull();
    assertNull(jsonArray.getValue(10));
    try {
      jsonArray.getValue(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
    try {
      jsonArray.getValue(11);
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
    try {
      jsonArray.add((JsonObjectTest.SomeEnum)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddString() {
    assertSame(jsonArray, jsonArray.add("foo"));
    assertEquals("foo", jsonArray.getString(0));
    try {
      jsonArray.add((String)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddCharSequence() {
    assertSame(jsonArray, jsonArray.add(new StringBuilder("bar")));
    assertEquals("bar", jsonArray.getString(0));
    try {
      jsonArray.add((CharSequence) null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddInteger() {
    assertSame(jsonArray, jsonArray.add(123));
    assertEquals(Integer.valueOf(123), jsonArray.getInteger(0));
    try {
      jsonArray.add((Integer)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddLong() {
    assertSame(jsonArray, jsonArray.add(123l));
    assertEquals(Long.valueOf(123l), jsonArray.getLong(0));
    try {
      jsonArray.add((Long)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddFloat() {
    assertSame(jsonArray, jsonArray.add(123f));
    assertEquals(Float.valueOf(123f), jsonArray.getFloat(0));
    try {
      jsonArray.add((Float)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddDouble() {
    assertSame(jsonArray, jsonArray.add(123d));
    assertEquals(Double.valueOf(123d), jsonArray.getDouble(0));
    try {
      jsonArray.add((Double)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddBoolean() {
    assertSame(jsonArray, jsonArray.add(true));
    assertEquals(true, jsonArray.getBoolean(0));
    jsonArray.add(false);
    assertEquals(false, jsonArray.getBoolean(1));
    try {
      jsonArray.add((Boolean)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddJsonObject() {
    JsonObject obj = new JsonObject().put("foo", "bar");
    assertSame(jsonArray, jsonArray.add(obj));
    assertEquals(obj, jsonArray.getJsonObject(0));
    try {
      jsonArray.add((JsonObject)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddJsonArray() {
    JsonArray arr = new JsonArray().add("foo");
    assertSame(jsonArray, jsonArray.add(arr));
    assertEquals(arr, jsonArray.getJsonArray(0));
    try {
      jsonArray.add((JsonArray)null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddBinary() {
    byte[] bytes = TestUtils.randomByteArray(10);
    assertSame(jsonArray, jsonArray.add(bytes));
    assertTrue(TestUtils.byteArraysEqual(bytes, jsonArray.getBinary(0)));
    try {
      jsonArray.add((byte[])null);
      fail();
    } catch (NullPointerException e) {
      // OK
    }
  }

  @Test
  public void testAddObject() {
    jsonArray.add((Object)"bar");
    jsonArray.add((Object)(Integer.valueOf(123)));
    jsonArray.add((Object)(Long.valueOf(123l)));
    jsonArray.add((Object)(Float.valueOf(1.23f)));
    jsonArray.add((Object)(Double.valueOf(1.23d)));
    jsonArray.add((Object)true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add((Object)(bytes));
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
    assertTrue(TestUtils.byteArraysEqual(bytes, jsonArray.getBinary(6)));
    assertEquals(obj, jsonArray.getJsonObject(7));
    assertEquals(arr, jsonArray.getJsonArray(8));
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
  public void testEncode() throws Exception {
    jsonArray.add("foo");
    jsonArray.add(123);
    jsonArray.add(1234l);
    jsonArray.add(1.23f);
    jsonArray.add(2.34d);
    jsonArray.add(true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String expected = "[\"foo\",123,1234,1.23,2.34,true,\"" + strBytes + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]";
    String json = jsonArray.encode();
    assertEquals(expected, json);
  }

  @Test
  public void testDecode() {
    byte[] bytes = TestUtils.randomByteArray(10);
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String json = "[\"foo\",123,1234,1.23,2.34,true,\"" + strBytes + "\",null,{\"foo\":\"bar\"},[\"foo\",123]]";
    JsonArray arr = new JsonArray(json);
    assertEquals("foo", arr.getString(0));
    assertEquals(Integer.valueOf(123), arr.getInteger(1));
    assertEquals(Long.valueOf(1234l), arr.getLong(2));
    assertEquals(Float.valueOf(1.23f), arr.getFloat(3));
    assertEquals(Double.valueOf(2.34d), arr.getDouble(4));
    assertEquals(true, arr.getBoolean(5));
    assertTrue(TestUtils.byteArraysEqual(bytes, arr.getBinary(6)));
    assertTrue(arr.hasNull(7));
    JsonObject obj = arr.getJsonObject(8);
    assertEquals("bar", obj.getString("foo"));
    JsonArray arr2 = arr.getJsonArray(9);
    assertEquals("foo", arr2.getString(0));
    assertEquals(Integer.valueOf(123), arr2.getInteger(1));
  }

  @Test
  public void testEncodePrettily() throws Exception {
    jsonArray.add("foo");
    jsonArray.add(123);
    jsonArray.add(1234l);
    jsonArray.add(1.23f);
    jsonArray.add(2.34d);
    jsonArray.add(true);
    byte[] bytes = TestUtils.randomByteArray(10);
    jsonArray.add(bytes);
    jsonArray.addNull();
    jsonArray.add(new JsonObject().put("foo", "bar"));
    jsonArray.add(new JsonArray().add("foo").add(123));
    String strBytes = Base64.getEncoder().encodeToString(bytes);
    String expected = "[ \"foo\", 123, 1234, 1.23, 2.34, true, \"" + strBytes + "\", null, {" + Utils.LINE_SEPARATOR +
      "  \"foo\" : \"bar\"" + Utils.LINE_SEPARATOR +
      "}, [ \"foo\", 123 ] ]";
    String json = jsonArray.encodePrettily();
    assertEquals(expected, json);
  }

  @Test
  public void testToString() {
    jsonArray.add("foo").add(123);
    assertEquals(jsonArray.encode(), jsonArray.toString());
  }

  // Strict JSON doesn't allow comments but we do so users can add comments to config files etc
  @Test
  public void testCommentsInJson() {
    String jsonWithComments =
      "// single line comment\n" +
        "/*\n" +
        "  This is a multi \n" +
        "  line comment\n" +
        "*/\n" +
        "[\n" +
        "// another single line comment this time inside the JSON array itself\n" +
        "  \"foo\", \"bar\" // and a single line comment at end of line \n" +
        "/*\n" +
        "  This is a another multi \n" +
        "  line comment this time inside the JSON array itself\n" +
        "*/\n" +
        "]";
    JsonArray json = new JsonArray(jsonWithComments);
    assertEquals("[\"foo\",\"bar\"]", json.encode());
  }

  @Test
  public void testInvalidJson() {
    String invalid = "qiwjdoiqwjdiqwjd";
    try {
      new JsonArray(invalid);
      fail();
    } catch (DecodeException e) {
      // OK
    }
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

}
