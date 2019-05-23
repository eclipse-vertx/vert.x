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
package io.vertx.core.json.pointer.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.core.json.pointer.JsonPointerIterator;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Francesco Guardiani @slinkydeveloper
 */
public class JsonPointerTest {

  @Test
  public void testParsing() {
    JsonPointer pointer = JsonPointer.from("/hello/world");
    assertEquals("/hello/world", pointer.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParsingErrorWrongFirstElement() {
    JsonPointer.from("bla/hello/world");
  }

  @Test
  public void testEncodingParsing() {
    JsonPointer pointer = JsonPointer.create().append("hell/o").append("worl~d");
    assertEquals("/hell~1o/worl~0d", pointer.toString());
  }

  @Test
  public void testURIParsing() {
    JsonPointer pointer = JsonPointer.fromURI(URI.create("http://www.example.org#/hello/world"));
    assertEquals("/hello/world", pointer.toString());
    assertEquals(URI.create("http://www.example.org#/hello/world"), pointer.toURI());
  }

  @Test
  public void testURIEncodedParsing() {
    JsonPointer pointer = JsonPointer.fromURI(URI.create("http://www.example.org#/hello/world/%5Ea"));
    assertEquals("/hello/world/^a", pointer.toString());
    assertEquals(URI.create("http://www.example.org#/hello/world/%5Ea"), pointer.toURI());
  }

  @Test
  public void testURIJsonPointerEncodedParsing() {
    JsonPointer pointer = JsonPointer.fromURI(URI.create("http://www.example.org#/hell~1o/worl~0d"));
    assertEquals("/hell~1o/worl~0d", pointer.toString());
    assertEquals(URI.create("http://www.example.org#/hell~1o/worl~0d"), pointer.toURI());
  }

  @Test
  public void testBuilding() {
    List<String> keys = new ArrayList<>();
    keys.add("hello");
    keys.add("world");
    JsonPointer pointer = new JsonPointerImpl(URI.create("#"), keys);
    assertEquals("/hello/world", pointer.toString());
  }

  @Test
  public void testURIBuilding() {
    JsonPointer pointer = JsonPointer.create().append("hello").append("world");
    assertEquals(URI.create("#/hello/world"), pointer.toURI());
  }

  @Test
  public void testEmptyBuilding() {
    JsonPointer pointer = JsonPointer.create();
    assertEquals("", pointer.toString());
    assertEquals(URI.create("#"), pointer.toURI());
  }

  @Test
  public void testAppendOtherPointer() {
    JsonPointer firstPointer = JsonPointer.fromURI(URI.create("http://example.com/stuff.json#/hello")).append("world");
    JsonPointer otherPointer = JsonPointer.fromURI(URI.create("http://example.com/other.json#/francesco"));
    firstPointer.append(otherPointer);
    assertEquals(URI.create("http://example.com/stuff.json#/hello/world/francesco"), firstPointer.toURI());
  }

  @Test
  public void testNullQuerying() {
    JsonPointer pointer = JsonPointer.from("/hello/world");
    assertNull(pointer.queryJson(null));
  }

  @Test
  public void testNullQueryingRootPointer() {
    JsonPointer pointer = JsonPointer.create();
    assertNull(pointer.queryJson(null));
  }

  @Test
  public void testNullQueryingRootPointerDefault() {
    JsonPointer pointer = JsonPointer.create();
    assertEquals(1, pointer.queryJsonOrDefault(null, 1));
  }

  @Test
  public void testJsonObjectQuerying() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    JsonPointer pointer = JsonPointer.from("/hello/world");
    assertEquals(1, pointer.queryJson(obj));
  }

  @Test
  public void testJsonObjectQueryingDefaultValue() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    JsonPointer pointer = JsonPointer.from("/hello/world/my/friend");
    assertEquals(1, pointer.queryJsonOrDefault(obj, 1));
  }

  @Test
  public void testJsonArrayQuerying() {
    JsonArray array = new JsonArray();
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 2).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));
    assertEquals(1, JsonPointer.from("/1/hello/world").queryJson(array));
    assertEquals(1, JsonPointer.fromURI(URI.create("#/1/hello/world")).queryJson(array));
  }

  @Test
  public void testJsonArrayQueryingOrDefault() {
    JsonArray array = new JsonArray();
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 2).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));
    assertEquals(1, JsonPointer.from("/5/hello/world").queryJsonOrDefault(array, 1));
  }

  @Test
  public void testRootPointer() {
    JsonPointer pointer = JsonPointer.create();
    JsonArray array = new JsonArray();
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 2).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    array.add(obj);
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));

    assertEquals(array, pointer.queryJson(array));
    assertEquals(obj, pointer.queryJson(obj));
    assertEquals("hello", pointer.queryJson("hello"));
  }

  @Test
  public void testRootPointerWrite() {
    JsonPointer pointer = JsonPointer.create();
    JsonObject obj = new JsonObject();
    JsonArray arr = new JsonArray();
    assertSame(arr, pointer.writeJson(obj, arr, false));
  }

  @Test
  public void testWrongUsageOfDashForQuerying() {
    JsonArray array = new JsonArray();
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 2).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));
    array.add(new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      ));
    JsonPointer pointer = JsonPointer.from("/-/hello/world");
    assertNull(pointer.queryJson(array));
  }

  /*
    The following JSON strings evaluate to the accompanying values:

    ""           // the whole document
    "/foo"       ["bar", "baz"]
    "/foo/0"     "bar"
    "/"          0
    "/a~1b"      1
    "/c%d"       2
    "/e^f"       3
    "/g|h"       4
    "/i\\j"      5
    "/k\"l"      6
    "/ "         7
    "/m~0n"      8

   */
  @Test
  public void testRFCExample() {
    JsonObject obj = new JsonObject("   {\n" +
      "      \"foo\": [\"bar\", \"baz\"],\n" +
      "      \"\": 0,\n" +
      "      \"a/b\": 1,\n" +
      "      \"c%d\": 2,\n" +
      "      \"e^f\": 3,\n" +
      "      \"g|h\": 4,\n" +
      "      \"i\\\\j\": 5,\n" +
      "      \"k\\\"l\": 6,\n" +
      "      \" \": 7,\n" +
      "      \"m~n\": 8\n" +
      "   }");

    assertEquals(obj, JsonPointer.from("").queryJson(obj));
    assertEquals(obj.getJsonArray("foo"), JsonPointer.from("/foo").queryJson(obj));
    assertEquals(obj.getJsonArray("foo").getString(0), JsonPointer.from("/foo/0").queryJson(obj));
    assertEquals(obj.getInteger(""), JsonPointer.from("/").queryJson(obj));
    assertEquals(obj.getInteger("a/b"), JsonPointer.from("/a~1b").queryJson(obj));
    assertEquals(obj.getInteger("c%d"), JsonPointer.from("/c%d").queryJson(obj));
    assertEquals(obj.getInteger("e^f"), JsonPointer.from("/e^f").queryJson(obj));
    assertEquals(obj.getInteger("g|h"), JsonPointer.from("/g|h").queryJson(obj));
    assertEquals(obj.getInteger("i\\\\j"), JsonPointer.from("/i\\\\j").queryJson(obj));
    assertEquals(obj.getInteger("k\\\"l"), JsonPointer.from("/k\\\"l").queryJson(obj));
    assertEquals(obj.getInteger(" "), JsonPointer.from("/ ").queryJson(obj));
    assertEquals(obj.getInteger("m~n"), JsonPointer.from("/m~0n").queryJson(obj));
  }

  @Test
  public void testWriteJsonObject() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(obj, JsonPointer.from("/hello/francesco").writeJson(obj, toInsert));
    assertEquals(toInsert, JsonPointer.from("/hello/francesco").queryJson(obj));
  }

  @Test
  public void testWriteWithCreateOnMissingJsonObject() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(obj, JsonPointer.from("/hello/users/francesco").write(obj, JsonPointerIterator.JSON_ITERATOR, toInsert, true));
    assertEquals(toInsert, JsonPointer.from("/hello/users/francesco").queryJson(obj));
  }

  @Test
  public void testWriteJsonObjectOverride() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(obj, JsonPointer.from("/hello/world").writeJson(obj, toInsert));
    assertEquals(toInsert, JsonPointer.from("/hello/world").queryJson(obj));
  }

  @Test
  public void testWriteJsonArray() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", new JsonObject()).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    JsonArray array = new JsonArray();
    array.add(obj.copy());
    array.add(obj.copy());
    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(array, JsonPointer.from("/0/hello/world/francesco").writeJson(array, toInsert));
    assertEquals(toInsert, JsonPointer.from("/0/hello/world/francesco").queryJson(array));
    assertNotEquals(array.getValue(0), array.getValue(1));
  }

  @Test
  public void testWriteJsonArrayAppend() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    JsonArray array = new JsonArray();
    array.add(obj.copy());
    array.add(obj.copy());
    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(array, JsonPointer.from("/-").writeJson(array, toInsert));
    assertEquals(toInsert, JsonPointer.from("/2").queryJson(array));
    assertEquals(array.getValue(0), array.getValue(1));
  }

  @Test
  public void testWriteJsonArraySubstitute() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    JsonArray array = new JsonArray();
    array.add(obj.copy());
    array.add(obj.copy());
    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(array, JsonPointer.from("/0").writeJson(array, toInsert));
    assertEquals(toInsert, JsonPointer.from("/0").queryJson(array));
    assertNotEquals(array.getValue(0), array.getValue(1));
  }

  @Test
  public void testNestedWriteJsonArraySubstitute() {
    JsonObject obj = new JsonObject()
      .put("hello",
        new JsonObject().put("world", 1).put("worl", "wrong")
      ).put("helo",
        new JsonObject().put("world", "wrong").put("worl", "wrong")
      );
    JsonArray array = new JsonArray();
    array.add(obj.copy());
    array.add(obj.copy());
    JsonObject root = new JsonObject().put("array", array);

    Object toInsert = new JsonObject().put("github", "slinkydeveloper");
    assertEquals(root, JsonPointer.from("/array/0").writeJson(root, toInsert));
    assertEquals(toInsert, JsonPointer.from("/array/0").queryJson(root));
  }

  @Test
  public void testIsParent() {
    JsonPointer parent = JsonPointer.fromURI(URI.create("yaml/valid/refs/Circular.yaml#/properties"));
    JsonPointer child = JsonPointer.fromURI(URI.create("yaml/valid/refs/Circular.yaml#/properties/parent"));
    assertTrue(parent.isParent(child));
    assertFalse(child.isParent(parent));
  }

  @Test
  public void testIsParentDifferentURI() {
    JsonPointer parent = JsonPointer.fromURI(URI.create("yaml/valid/refs/Circular.yaml#/properties"));
    JsonPointer child = JsonPointer.fromURI(URI.create("json/valid/refs/Circular.yaml#/properties/parent"));
    assertFalse(parent.isParent(child));
    assertFalse(child.isParent(parent));
  }

  @Test
  public void testIsParentWithRootPointer() {
    JsonPointer parent = JsonPointer.fromURI(URI.create("yaml/valid/refs/Circular.yaml#"));
    JsonPointer child = JsonPointer.fromURI(URI.create("yaml/valid/refs/Circular.yaml#/properties/parent"));
    assertTrue(parent.isParent(child));
    assertFalse(child.isParent(parent));
  }

  @Test
  public void testTracedQuery() {
    JsonObject child2 = new JsonObject().put("child3", 1);
    JsonArray child1 = new JsonArray().add(child2);
    JsonObject root = new JsonObject().put("child1", child1);

    JsonPointer pointer = JsonPointer
      .create()
      .append("child1")
      .append("0")
      .append("child3");

    List<Object> traced = pointer.tracedQuery(root, JsonPointerIterator.JSON_ITERATOR);
    assertEquals(4, traced.size());
    assertSame(root, traced.get(0));
    assertSame(child1, traced.get(1));
    assertSame(child2, traced.get(2));
    assertEquals(1, traced.get(3));
  }

  @Test
  public void testEmptyTracedQuery() {
    JsonPointer pointer = JsonPointer
      .create()
      .append("child1")
      .append("0")
      .append("child3");

    List<Object> traced = pointer.tracedQuery(null, JsonPointerIterator.JSON_ITERATOR);
    assertTrue(traced.isEmpty());
  }

  @Test
  public void testNotFoundTracedQuery() {
    JsonObject child2 = new JsonObject().put("child5", 1);
    JsonArray child1 = new JsonArray().add(child2);
    JsonObject root = new JsonObject().put("child1", child1);

    JsonPointer pointer = JsonPointer
      .create()
      .append("child1")
      .append("0")
      .append("child3");

    List<Object> traced = pointer.tracedQuery(root, JsonPointerIterator.JSON_ITERATOR);
    assertEquals(3, traced.size());
    assertSame(root, traced.get(0));
    assertSame(child1, traced.get(1));
    assertSame(child2, traced.get(2));
  }

}
