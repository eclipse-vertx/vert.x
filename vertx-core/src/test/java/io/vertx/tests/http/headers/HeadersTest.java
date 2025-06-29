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

package io.vertx.tests.http.headers;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.*;
import static org.junit.Assert.assertNotSame;

public abstract class HeadersTest {

  protected abstract MultiMap newMultiMap();

  @Test
  public void testCaseInsensitiveHeaders() {
    MultiMap result = newMultiMap();
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testAddAll1() {
    MultiMap mmap = newMultiMap();
    HashMap<String, String> map = new HashMap<>();
    map.put("a", "b");
    MultiMap result = mmap.addAll(map);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("a=b\n", result.toString());
  }

  @Test
  public void testAddAll2() {
    MultiMap mmap = newMultiMap();
    HashMap<String, String> map = new HashMap<>();
    map.put("a", "b");
    map.put("c", "d");
    assertEquals("a=b\nc=d\n", sortByLine(mmap.addAll(map).toString()));
  }

  @Test
  public void testAddAllEmptyMap() {
    MultiMap mmap = newMultiMap();
    Map<String, String> map = new HashMap<>();
    assertEquals("", mmap.addAll(map).toString());
  }

  @Test
  public void testAddAllEmptyMultiMap() {
    MultiMap mmap = newMultiMap();
    MultiMap headers = newMultiMap();
    assertEquals("", mmap.addAll(headers).toString());
  }

  @Test
  public void testAddCharSequenceNameCharSequenceValue() {
    MultiMap mmap = newMultiMap();
    CharSequence name = "name";
    CharSequence value = "value";
    assertEquals("name=value\n", mmap.add(name, value).toString());
  }

  @Test
  public void testAddCharSequenceNameIterableCharSequenceValue() {
    MultiMap mmap = newMultiMap();
    CharSequence name = "name";
    ArrayList<CharSequence> values = new ArrayList<>();
    values.add("somevalue");
    assertEquals("name=somevalue\n", mmap.add(name, values).toString());
  }

  @Test
  public void testAddStringNameStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "a";
    String strVal = "b";
    assertEquals("a=b\n", mmap.add(name, strVal).toString());
  }

  @Test
  public void testAddStringNameEmptyStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "aaa";
    String strVal = "";
    assertEquals("aaa=\n", mmap.add(name, strVal).toString());
  }

  @Test(expected = NullPointerException.class)
  public void testAddNullStringValueTest() {
    MultiMap mmap = newMultiMap();
    mmap.add("name", (String) null);
  }

  @Test(expected = NullPointerException.class)
  public void testAddNullCharSequenceValueTest() {
    MultiMap mmap = newMultiMap();
    mmap.add("name", (CharSequence) null);
  }

  @Test(expected = NullPointerException.class)
  public void testAddNullIterableStringValueTest() {
    MultiMap mmap = newMultiMap();
    mmap.add("name", (Iterable<String>) null);
  }

  @Test
  public void testAddStringNameIterableStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "name";
    List<String> values = new ArrayList<>();
    values.add("value1");
    values.add("value2");
    MultiMap result = mmap.add(name, values);
    assertEquals(1, result.size());
    assertEquals("name=value1\nname=value2\n", result.toString());
  }

  @Test
  public void testAddAllMultiMap() {
    MultiMap mmap = newMultiMap();
    MultiMap mm = newMultiMap();
    mm.add("header1", "value1");
    mm.add("header2", "value2");
    MultiMap result = mmap.addAll(mm);
    assertEquals(2, result.size());
    assertEquals("header1=value1\nheader2=value2\n", result.toString());
  }

  @Test
  public void testClearTest1() {
    MultiMap mmap = newMultiMap();
    MultiMap result = mmap.clear();
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testContainsCharSequence() {
    MultiMap mmap = newMultiMap();
    CharSequence name = String.valueOf(new Object());
    assertFalse(mmap.contains(name));
  }

  @Test
  public void testContainsEmptyString() {
    MultiMap mmap = newMultiMap();
    String name = "";
    assertFalse(mmap.contains(name));
  }

  @Test
  public void testContainsEmptyValue() {
    MultiMap mmap = newMultiMap();
    String name = "0123456789";
    assertFalse(mmap.contains(name));
    mmap.add(name, "");
    assertTrue(mmap.contains(name));
  }

  @Test
  public void testEntriesEmpty() {
    MultiMap mmap = newMultiMap();
    List<Map.Entry<String, String>> result = mmap.entries();
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testGetMissingCharSequenceName() {
    MultiMap mmap = newMultiMap();
    CharSequence name = String.valueOf(new Object());
    assertNull(mmap.get(name));
  }

  @Test
  public void testGetMissingStringName() {
    MultiMap mmap = newMultiMap();
    String name = "1";
    assertNull(mmap.get(name));
  }

  @Test
  public void testGetStringName() {
    MultiMap mmap = newMultiMap();
    assertNull(mmap.get("name"));
    mmap.add("name", "value");
    assertEquals("value", mmap.get("name"));
  }

  @Test(expected = NullPointerException.class)
  public void testGetNullStringName() {
    newMultiMap().get(null);
  }

  @Test
  public void testGetAllTestMissingName() {
    MultiMap mmap = newMultiMap();
    List<String> result = mmap.getAll("1");
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testGetAll() {
    MultiMap mmap = newMultiMap();
    mmap.add("name", "value1");
    mmap.add("name", "value2");
    List<String> result = mmap.getAll("name");
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("value1", result.get(0));
  }

  @Test(expected = NullPointerException.class)
  public void testGetAllNullName() {
    newMultiMap().getAll(null);
  }

  @Test
  public void testIsEmptyEmpty() {
    MultiMap mmap = newMultiMap();
    assertTrue(mmap.isEmpty());
  }

  @Test
  public void testIsEmptyNonEmpty() {
    MultiMap mmap = newMultiMap();
    mmap.add("a", "b");
    assertFalse(mmap.isEmpty());
  }

  @Test
  public void testIteratorEmpty() {
    MultiMap mmap = newMultiMap();
    Iterator<Map.Entry<String, String>> result = mmap.iterator();
    assertNotNull(result);
    assertFalse(result.hasNext());
  }

  @Test
  public void testIterator() {
    MultiMap mmap = newMultiMap();
    mmap.add("a", "b");
    Iterator<Map.Entry<String, String>> result = mmap.iterator();
    assertNotNull(result);
    assertTrue(result.hasNext());
  }

  @Test
  public void testNamesEmpty() {
    MultiMap mmap = newMultiMap();
    Set<String> result = mmap.names();
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testRemoveMissingName() {
    MultiMap mmap = newMultiMap();
    CharSequence name = String.valueOf(new Object());
    MultiMap result = mmap.remove(name);
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
  }

  @Test(expected = NullPointerException.class)
  public void testRemoveNullName() {
    newMultiMap().remove(null);
  }

  @Test
  public void testRemove() {
    MultiMap mmap = newMultiMap();
    mmap.add("name", "value");
    assertTrue(mmap.contains("name"));
    assertSame(mmap, mmap.remove("name"));
    assertFalse(mmap.contains("name"));
  }

  @Test
  public void testSetAll() {
    MultiMap mmap = newMultiMap();
    HashMap<String, String> map = new HashMap<>();
    map.put("aaa", "bbb");
    MultiMap result = mmap.setAll(map);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("aaa=bbb\n", result.toString());
  }

  @Test
  public void testSetAllEmptyMap() {
    MultiMap mmap = newMultiMap();
    MultiMap result = mmap.setAll(new HashMap<>());
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testSetAllEmptyMultiMap() {
    MultiMap mmap = newMultiMap();
    MultiMap result = mmap.setAll(newMultiMap());
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testSetCharSequenceNameCharSequenceValue() {
    MultiMap mmap = newMultiMap();
    CharSequence name = "name";
    CharSequence value = "value";
    MultiMap result = mmap.set(name, value);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("name=value\n", result.toString());
  }

  @Test
  public void testSetCharSequenceNameIterableCharSequenceValue() {
    MultiMap mmap = newMultiMap();
    CharSequence name = "name";
    ArrayList<CharSequence> values = new ArrayList<>();
    values.add("somevalue");
    assertEquals("name=somevalue\n", mmap.set(name, values).toString());
  }

  @Test
  public void testSetStringNameStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "aaa";
    String strVal = "bbb";
    MultiMap result = mmap.set(name, strVal);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("aaa=bbb\n", result.toString());
  }

  @Test
  public void testSetStringNameEmptyStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "aaa";
    String strVal = "";
    MultiMap result = mmap.set(name, strVal);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("aaa=\n", result.toString());
  }

  @Test
  public void testSetNullCharSequenceValue() {
    MultiMap mmap = newMultiMap();
    mmap.set("name", "value");
    mmap.set("name", (CharSequence) null);
    assertFalse(mmap.contains("name"));
    assertTrue(mmap.isEmpty());
    assertFalse(mmap.iterator().hasNext());
  }

  @Test
  public void testSetNullStringValue() {
    MultiMap mmap = newMultiMap();
    mmap.set("name", "value");
    mmap.set("name", (String) null);
    assertFalse(mmap.contains("name"));
    assertTrue(mmap.isEmpty());
    assertFalse(mmap.iterator().hasNext());
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullIterableStringValue() {
    newMultiMap().set("name", (Iterable<String>) null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullIterableCharSequenceValue() {
    newMultiMap().set("name", (Iterable<String>) null);
  }

  @Test
  public void testSetEmptyIterableStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "name";
    List<String> values = new ArrayList<>();
    MultiMap result = mmap.set(name, values);
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testSetEmptyIterableCharSequenceValue() {
    MultiMap mmap = newMultiMap();
    String name = "name";
    List<CharSequence> values = new ArrayList<>();
    MultiMap result = mmap.set(name, values);
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testSetIterableStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "name";
    List<String> values = new ArrayList<>();
    values.add("value1");
    values.add(null);
    values.add("value3");
    MultiMap result = mmap.set(name, values);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("name=value1\n", result.toString());
  }

  @Test
  public void testSize() {
    MultiMap mmap = newMultiMap();
    assertEquals(0, mmap.size());
    mmap.add("header", "value");
    assertEquals(1, mmap.size());
    mmap.add("header2", "value2");
    assertEquals(2, mmap.size());
    mmap.add("header", "value3");
    assertEquals(2, mmap.size());
  }

  // we have to sort the string since a map doesn't do sorting
  private String sortByLine(String str) {
    String[] lines = str.split("\n");
    Arrays.sort(lines);
    StringBuilder sb = new StringBuilder();
    for (String s:lines) {
      sb.append(s);
      sb.append("\n");
    }
    return sb.toString();
  }

  @Test
  public void testToString() {
    MultiMap mm = newMultiMap();
    assertEquals("", mm.toString());
    mm.add("header1", "Value1");
    assertEquals("header1=Value1\n", sortByLine(mm.toString()));
    mm.add("header2", "Value2");
    assertEquals("header1=Value1\nheader2=Value2\n", sortByLine(mm.toString()));
    mm.add("header1", "Value3");
    assertEquals("header1=Value1\nheader1=Value3\nheader2=Value2\n", sortByLine(mm.toString()));
    mm.remove("header1");
    assertEquals("header2=Value2\n", sortByLine(mm.toString()));
    mm.set("header2", "Value4");
    assertEquals("header2=Value4\n", sortByLine(mm.toString()));
  }

  /*
   * unit tests for public method in MapEntry
   * (isn't actually used in the implementation)
   */

  @Test
  public void testMapEntrySetValue() {
    MultiMap mmap = newMultiMap();
    mmap.add("header", "oldvalue");
    for (Map.Entry<String, String> me : mmap) {
      me.setValue("newvalue");
    }
    assertEquals("newvalue", mmap.get("header"));
  }

  @Test
  public void testMapEntryToString() {
    MultiMap mmap = newMultiMap();
    mmap.add("header", "value");
    assertEquals("header=value", mmap.iterator().next().toString());
  }

  @Test(expected = NullPointerException.class)
  public void testMapEntrySetValueNull() {
    MultiMap mmap = newMultiMap();
    mmap.add("header", "oldvalue");
    for (Map.Entry<String, String> me:mmap) {
      me.setValue(null);
    }
  }

  @Test
  public void testContainsValueString() {
    MultiMap mmap = newMultiMap();
    mmap.add("headeR", "vAlue");
    assertTrue(mmap.contains("heaDer", "vAlue", false));
    assertFalse(mmap.contains("heaDer", "Value", false));
  }

  @Test
  public void testContainsValueStringIgnoreCase() {
    MultiMap mmap = newMultiMap();
    mmap.add("headeR", "vAlue");
    assertTrue(mmap.contains("heaDer", "vAlue", true));
    assertTrue(mmap.contains("heaDer", "Value", true));
  }

  @Test
  public void testContainsValueCharSequence() {
    MultiMap mmap = newMultiMap();
    mmap.add("headeR", "vAlue");
    CharSequence name = HttpHeaders.createOptimized("heaDer");
    CharSequence vAlue = HttpHeaders.createOptimized("vAlue");
    CharSequence Value = HttpHeaders.createOptimized("Value");
    assertTrue(mmap.contains(name, vAlue, false));
    assertFalse(mmap.contains(name, Value, false));
  }

  @Test
  public void testContainsValueCharSequenceIgnoreCase() {
    MultiMap mmap = newMultiMap();
    mmap.add("headeR", "vAlue");
    CharSequence name = HttpHeaders.createOptimized("heaDer");
    CharSequence vAlue = HttpHeaders.createOptimized("vAlue");
    CharSequence Value = HttpHeaders.createOptimized("Value");
    assertTrue(mmap.contains(name, vAlue, true));
    assertTrue(mmap.contains(name, Value, true));
  }

  @Test
  public void testGetShouldReturnAddedEntriesInOrder() {
    MultiMap mmap = newMultiMap();
    mmap.add("header", "value1");
    mmap.add("header", "value2");
    mmap.add("header", "value3");
    assertEquals("value1", mmap.get("header"));
    assertEquals(Arrays.asList("value1", "value2", "value3"), mmap.getAll("header"));
  }

  @Test
  public void testGetShouldReturnEntriesFromListInOrder() {
    MultiMap mmap = newMultiMap();
    mmap.add("header", Arrays.<CharSequence>asList("value1", "value2", "value3"));
    assertEquals("value1", mmap.get("header"));
    assertEquals(Arrays.asList("value1", "value2", "value3"), mmap.getAll("header"));
  }

  @Test
  public void testIterableArgument() {
    MultiMap mmap = newMultiMap();
    mmap.add("header", Arrays.<String>asList("value1", "value2"));
    assertEquals(Arrays.asList("value1", "value2"), mmap.getAll("header"));
    mmap = newMultiMap();
    mmap.add("header", Arrays.asList(HttpHeaders.createOptimized("value1"), HttpHeaders.createOptimized("value2")));
    assertEquals(Arrays.asList("value1", "value2"), mmap.getAll("header"));
    mmap = newMultiMap();
    mmap.set("header", Arrays.<CharSequence>asList("value1", "value2"));
    assertEquals(Arrays.asList("value1", "value2"), mmap.getAll("header"));
    mmap = newMultiMap();
    mmap.set("header", Arrays.asList(HttpHeaders.createOptimized("value1"), HttpHeaders.createOptimized("value2")));
    assertEquals(Arrays.asList("value1", "value2"), mmap.getAll("header"));
  }

  @Test
  public void testSetAllOnExistingMapUsingMultiMapHttp1() {
    MultiMap mainMap = new HeadersAdaptor(HeadersMultiMap.httpHeaders());
    mainMap.add("originalKey", "originalValue");

    MultiMap setAllMap = newMultiMap();

    setAllMap.add("originalKey", "newValue");
    setAllMap.add("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testSetAllOnExistingMapUsingHashMapHttp1() {
    MultiMap mainMap = new HeadersAdaptor(HeadersMultiMap.httpHeaders());
    mainMap.add("originalKey", "originalValue");

    Map<String,String> setAllMap = new HashMap<>();

    setAllMap.put("originalKey", "newValue");
    setAllMap.put("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testSetAllOnExistingMapUsingMultiMapHttp2() {
    MultiMap mainMap = new Http2HeadersMultiMap(new DefaultHttp2Headers());
    mainMap.add("originalKey", "originalValue");

    MultiMap setAllMap = newMultiMap();

    setAllMap.add("originalKey", "newValue");
    setAllMap.add("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testSetAllOnExistingMapUsingHashMapHttp2() {
    MultiMap mainMap = new Http2HeadersMultiMap(new DefaultHttp2Headers());
    mainMap.add("originalKey", "originalValue");

    Map<String,String> setAllMap = new HashMap<>();

    setAllMap.put("originalKey", "newValue");
    setAllMap.put("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testForEach() {
    MultiMap map = newMultiMap();

    map.add(HttpHeaders.ACCEPT, HttpHeaders.TEXT_HTML);
    map.add(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XML);

    Map<String, String> consumed = new HashMap<>();
    map.forEach(consumed::put);

    assertThat(consumed).containsOnly(entry("accept", "text/html"), entry("content-type", "application/xml"));
  }

  @Test
  public void testImmutableCopy() {
    MultiMap mutable = newMultiMap();
    mutable.set("foo", "foo1");
    mutable.set("bar", (Iterable<String>) Arrays.asList("bar1", "bar2"));
    MultiMap immutableCopy = mutable.copy(false);
    assertNotSame(mutable, immutableCopy);
    assertEquals(mutable.toString(), immutableCopy.toString());
    try {
      immutableCopy.set("foo", "foo2");
    } catch (IllegalStateException expected) {
      assertEquals("foo1", immutableCopy.get("foo"));
    }
    mutable.add("foo", "foo2");
    assertNotEquals(mutable.toString(), immutableCopy.toString());
    assertSame(immutableCopy, immutableCopy.copy(false));
    MultiMap mutableCopy = immutableCopy.copy(true);
    assertNotSame(immutableCopy, mutableCopy);
    assertEquals(immutableCopy.toString(), mutableCopy.toString());
    mutableCopy.add("foo", "foo2");
    assertEquals(mutable.toString(), mutableCopy.toString());
  }

  @Test
  public void testMutableCopy() {
    MultiMap mutable = newMultiMap();
    mutable.set("foo", "foo1");
    mutable.set("bar", (Iterable<String>) Arrays.asList("bar1", "bar2"));
    MultiMap mutableCopy = mutable.copy(true);
    assertNotSame(mutable, mutableCopy);
    assertEquals(mutable.toString(), mutableCopy.toString());
    mutableCopy.set("foo", "foo2");
    assertEquals("foo1", mutable.get("foo"));
    assertEquals("foo2", mutableCopy.get("foo"));
  }

  @Test
  public void testContainsKeyAndValueEmpty() {
    MultiMap map = newMultiMap();
    assertFalse(map.contains("foo", "bar", false));
  }
}
