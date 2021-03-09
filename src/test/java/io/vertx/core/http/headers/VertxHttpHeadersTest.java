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

package io.vertx.core.http.headers;

import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttpHeadersTest extends HeadersTestBase {

  // Same hash
  protected String sameHash1;
  protected String sameHash2;

  // Different hash / same bucket
  protected String sameBucket1;
  protected String sameBucket2;

  public VertxHttpHeadersTest() {
    sameHash1 = "ABCDEF";
    sameHash2 = "HOBOURN";
    sameBucket1 = "ZEITOUN";
    sameBucket2 = "AAKSUHX";
  }

  @Override
  protected HeadersMultiMap newMultiMap() {
    return HeadersMultiMap.httpHeaders();
  }

  @Test
  public void checkNameCollision() {
    assertEquals(AsciiString.hashCode(sameHash1), AsciiString.hashCode(sameHash2));
    assertEquals(AsciiString.hashCode(sameBucket1) & 0xF, AsciiString.hashCode(sameBucket2) & 0xF);
    assertNotEquals(AsciiString.hashCode(sameBucket1), AsciiString.hashCode(sameBucket2));
  }

  @Test
  public void testAddEmptyStringNameIterableStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "";
    ArrayList<CharSequence> values = new ArrayList<>();
    values.add("somevalue");
    assertEquals("=somevalue\n", mmap.add(name, values).toString());
  }


  @Test
  public void testAddEmptyStringNameEmptyStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "";
    String strVal = "";
    assertEquals("=\n", mmap.add(name, strVal).toString());
  }

  @Test
  public void testAddEmptyStringName() {
    MultiMap mmap = newMultiMap();
    String name = "";
    String strVal = "aaa";
    assertEquals("=aaa\n", mmap.add(name, strVal).toString());
  }

  @Test
  public void testSetAllEmptyStringNameAndEmptyValue() {
    MultiMap mmap = newMultiMap();
    HashMap<String, String> headers = new HashMap<>();
    headers.put("", "");
    MultiMap result = mmap.setAll(headers);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("=\n", result.toString());
  }

  @Test
  public void testSetEmptyStringNameAndEmptyValue() {
    MultiMap mmap = newMultiMap();
    String name = "";
    String strVal = "";
    MultiMap result = mmap.set(name, strVal);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("=\n", result.toString());
  }

  @Test
  public void testSetEmptyStringName() {
    MultiMap mmap = newMultiMap();
    String name = "";
    String strVal = "bbb";
    MultiMap result = mmap.set(name, strVal);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("=bbb\n", result.toString());
  }

  @Test
  public void testSetAll() {
    MultiMap mmap = newMultiMap();
    HashMap<String, String> headers = new HashMap<>();
    headers.put("", "");
    headers.put("aaa", "bbb");
    MultiMap result = mmap.setAll(headers);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("=\naaa=bbb\n", result.toString());
  }

  @Test
  public void testSetEmptyStringNameIterableStringValue() {
    MultiMap mmap = newMultiMap();
    String name = "";
    ArrayList<CharSequence> values = new ArrayList<>();
    values.add("somevalue");
    assertEquals("=somevalue\n", mmap.set(name, values).toString());
  }

  @Test
  public void testGetHashColl() {
    MultiMap mm = newMultiMap();
    String name1 = this.sameHash1;
    String name2 = this.sameHash2;
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));
    // same bucket, different hash
    mm = newMultiMap();
    name1 = this.sameBucket1;
    name2 = this.sameBucket2;
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));
  }

  @Test
  public void testGetAllHashColl() {
    MultiMap mm = newMultiMap();
    String name1 = this.sameHash1;
    String name2 = this.sameHash2;
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("[value1]", mm.getAll(name1).toString());
    assertEquals("[value2]", mm.getAll(name2).toString());
    mm = newMultiMap();
    name1 = this.sameBucket1;
    name2 = this.sameBucket2;
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("[value1]", mm.getAll(name1).toString());
    assertEquals("[value2]", mm.getAll(name2).toString());
  }

  @Test
  public void testRemoveHashColl() {
    MultiMap mm = newMultiMap();
    String name1 = this.sameHash1;
    String name2 = this.sameHash2;
    String name3 = "RZ";
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    mm.add(name3, "value3");
    mm.add(name1, "value4");
    mm.add(name2, "value5");
    mm.add(name3, "value6");
    assertEquals(3, mm.size());
    mm.remove(name1);
    mm.remove(name2);
    assertEquals(1, mm.size());
    mm = newMultiMap();
    name1 = this.sameBucket1;
    name2 = this.sameBucket2;
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    mm.remove(name1);
    mm.remove(name2);
    assertTrue("not empty", mm.isEmpty());
  }

  @Test
  public void testRemovalNext() {
    MultiMap mmap = newMultiMap();
    String name1 = this.sameHash1;
    String name2 = this.sameHash2;
    mmap.add(name1, "v");
    mmap.add(name1, "v");
    mmap.add(name2, "q");
    mmap.remove(name1);
    mmap.set(name1, "w");
    assertEquals("w", mmap.get(name1));
  }

  @Test
  public void testNonCharSequenceValue() {
    HeadersMultiMap mmap = newMultiMap();
    mmap.set("key1", 0);
    assertEquals("0", mmap.get("key1"));
    mmap.set((CharSequence) "key2", 1);
    assertEquals("1", mmap.get("key2"));
    mmap.set("key3", Arrays.asList(2, 3));
    assertEquals("2", mmap.get("key3"));
    mmap.set((CharSequence) "key4", Arrays.asList(4, 5));
    assertEquals("4", mmap.get("key4"));
    mmap.add("key5", 6);
    assertEquals("6", mmap.get("key5"));
    mmap.add((CharSequence) "key6", 7);
    assertEquals("7", mmap.get("key6"));
    mmap.add("key8", Arrays.asList(2, 3));
    assertEquals("2", mmap.get("key8"));
    mmap.add((CharSequence) "key9", Arrays.asList(4, 5));
    assertEquals("4", mmap.get("key9"));
  }
}
