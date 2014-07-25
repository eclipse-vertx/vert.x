/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.VertxException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonElement;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.BiFunction;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JSONTest {

  @Test
  public void testJsonObject() throws Exception {
    JsonObject obj = new JsonObject().putString("foo", "bar");
    String str = obj.encode();
    JsonObject obj2 = new JsonObject(str);
    assertEquals("bar", obj2.getString("foo"));
  }

  @Test
  public void testJsonArrayToClone() {
    JsonArray array = new JsonArray();
    array.add("test");
    JsonObject object = new JsonObject();
    object.putArray("array", array);

    //want to clone
    JsonObject object2 = object.copy();

    JsonArray array2 = object2.getArray("array");
    assertNotSame(array, array2);
  }

  @Test
  public void testRetrieveArrayItemByIndex() {
    JsonArray arr = new JsonArray();
    arr.addString("foo");
    arr.addObject(new JsonObject().putString("bar", "baz"));
    arr.addString("bap");
    assertEquals("baz", ((JsonObject) arr.get(1)).getString("bar"));
    assertEquals("bap", arr.get(2));
    assertEquals("foo", arr.get(0));
  }

  @Test
  public void testPutsNullObject() {
    String nullEncode = "{\"null\":null}";
    assertEquals(nullEncode, new JsonObject().putString("null", null).encode());
    assertEquals(nullEncode, new JsonObject().putObject("null", null).encode());
    assertEquals(nullEncode, new JsonObject().putArray("null", null).encode());
    assertEquals(nullEncode, new JsonObject().putElement("null", null).encode());
    assertEquals(nullEncode, new JsonObject().putNumber("null", null).encode());
    assertEquals(nullEncode, new JsonObject().putBoolean("null", null).encode());
    assertEquals(nullEncode, new JsonObject().putValue("null", null).encode());
  }

  @Test
  public void testNullValuesInArray() {
    assertNull(new JsonArray().add(null).get(0));
    assertNull(new JsonArray().addArray(null).get(0));
    assertNull(new JsonArray().addBoolean(null).get(0));
    assertNull(new JsonArray().addBinary(null).get(0));
    assertNull(new JsonArray().addElement(null).get(0));
    assertNull(new JsonArray().addNumber(null).get(0));
    assertNull(new JsonArray().addObject(null).get(0));
    assertNull(new JsonArray().addString(null).get(0));
  }

  @Test
  public void testGetNullValues() {
    assertNull(new JsonObject().putString("foo", null).getString("foo"));
    assertNull(new JsonObject().putObject("foo", null).getObject("foo"));
    assertNull(new JsonObject().putArray("foo", null).getArray("foo"));
    assertNull(new JsonObject().putElement("foo", null).getElement("foo"));
    assertNull(new JsonObject().putNumber("foo", null).getNumber("foo"));
    assertNull(new JsonObject().putBoolean("foo", null).getBoolean("foo"));
    assertNull(new JsonObject().putBinary("foo", null).getBinary("foo"));
    assertNull(new JsonObject().putValue("foo", null).getValue("foo"));
  }

  @Test
  public void testContainsNullValues() {
    assertTrue(new JsonObject().putString("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putObject("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putArray("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putElement("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putNumber("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putBoolean("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putBinary("foo", null).containsField("foo"));
    assertTrue(new JsonObject().putValue("foo", null).containsField("foo"));
  }

  @Test
  public void testJsonElementTypeCheck(){
    JsonElement objElement = new JsonObject().putString("foo", "bar");
    JsonElement arrayElement = new JsonArray().addString("foo");
    assertTrue(objElement.isObject());
    assertFalse(objElement.isArray());
    assertFalse(arrayElement.isObject());
    assertTrue(arrayElement.isArray());
  }

  @Test
  public void testJsonElementConversionWithoutException(){
    JsonElement objElement = new JsonObject().putString("foo", "bar");
    JsonElement arrayElement = new JsonArray().addString("foo");
    JsonObject retrievedObject = objElement.asObject();
    JsonArray retrievedArray = arrayElement.asArray();
    assertEquals("{\"foo\":\"bar\"}", retrievedObject.encode());
    assertEquals("[\"foo\"]", retrievedArray.encode());
  }

  @Test
  public void testJsonElementConversionWithException(){
    JsonElement objElement = new JsonObject().putString("foo", "bar");
    try {
      objElement.asArray();
      fail("Coercing JsonElement(Object) into JsonArray did not throw a ClassCastException");
    } catch(ClassCastException e){
      // OK
    }
  }

  @Test
  public void testJsonElementConversionWithException2(){
    JsonElement arrayElement = new JsonArray().addString("foo");
    try {
      arrayElement.asObject();
      fail("Coercing JsonElement(Array) into JsonObject did not throw a ClassCastException");
    } catch(ClassCastException e){
      // OK
    }
  }


  @Test
  public void testInsertJsonElementIntoJsonObjectWithouException(){
    JsonObject objElement = new JsonObject().putString("foo", "bar");
    JsonArray arrayElement = new JsonArray().addString("foo");

    /* Insert an Object */
    assertEquals("{\"elementField\":{\"foo\":\"bar\"}}", new JsonObject().putElement("elementField", objElement).encode());

    /* Insert an Array */
    assertEquals("{\"elementField\":[\"foo\"]}", new JsonObject().putElement("elementField", arrayElement).encode());
  }

  @Test
  public void testRetrieveJsonElementFromJsonObject() {
    JsonObject objElement = new JsonObject().putString("foo", "bar");

    JsonObject tester = new JsonObject().putElement("elementField", objElement);

    JsonElement testElement = tester.getElement("elementField");

    assertEquals(objElement.getString("foo"), testElement.asObject().getString("foo"));
  }

  @Test
  public void testRetrieveJsonElementFromJsonObject2() {
    JsonArray arrayElement = new JsonArray().addString("foo");

    JsonObject tester = new JsonObject()
      .putElement("elementField", arrayElement);

    JsonElement testElement = tester.getElement("elementField");

    assertEquals((String)arrayElement.get(0), (String)testElement.asArray().get(0));
  }

  @Test
  public void testRetrieveJsonElementFromJsonObjectWithException(){
    JsonObject tester = new JsonObject().putString("elementField", "foo");
    try{
      tester.getElement("elementField");
      fail("Retrieving a field that is not a JsonElement did not throw ClassCastException");
    } catch(ClassCastException e){
      // OK
    }
  }

  @Test
  public void testInsertJsonElementIntoJsonArrayWithouException(){
    JsonObject objElement = new JsonObject().putString("foo", "bar");
    JsonArray arrayElement = new JsonArray().addString("foo");

    /* Insert an Object */
    assertEquals("[{\"foo\":\"bar\"}]", new JsonArray().addElement(objElement).encode());

    /* Insert an Array */
    assertEquals("[[\"foo\"]]", new JsonArray().addElement(arrayElement).encode());
  }

  @Test
  public void testRetrieveJsonElementFromJsonArray(){
    JsonObject objElement = new JsonObject().putString("foo", "bar");

    /* Insert an Object */
    JsonArray tester = new JsonArray()
      .addElement(objElement);

    JsonElement testElement = tester.get(0);

    assertEquals(objElement.getString("foo"), testElement.asObject().getString("foo"));
  }

  @Test
  public void testRetrieveJsonElementFromJsonArray2(){
    JsonArray arrayElement = new JsonArray().addString("foo");

    /* Insert an Object */
    JsonArray tester = new JsonArray()
      .addElement(arrayElement);

    JsonElement testElement = tester.get(0);

    assertEquals((String)arrayElement.get(0), (String)testElement.asArray().get(0));
  }

  @Test
  public void testJsonArraysOfJsonObjectsEquality() {
    JsonArray array1 = new JsonArray().addObject(new JsonObject("{ \"a\": \"b\" }"));
    JsonArray array2 = new JsonArray().addObject(new JsonObject("{ \"a\": \"b\" }"));

    assertEquals(array1, array2);
  }

  @Test
  public void testJsonArraysWithNullsEquality() {
    JsonArray array1 = new JsonArray(new Object[]{null, "a"});
    JsonArray array2 = new JsonArray(new Object[]{null, "a"});

    assertEquals(array1, array2);
  }

  @Test
  public void testJsonArraysWithNullsEquality2() {
    JsonArray array1 = new JsonArray(new Object[]{null, "a"});
    JsonArray array2 = new JsonArray(new Object[]{"b", "a"});

    assertFalse(array1.equals(array2));
  }

  @Test
  public void testGetBinary() {
    JsonObject json = new JsonObject();
    assertNull(json.getBinary("binary"));
  }

  @Test
  public void testCreateJsonArrayFromArray() {
    Object[] numbers = new Integer[]{1, 2, 3};
    // Json is not immutable
    JsonArray json = new JsonArray(numbers);
    json.add(4);
    assertEquals(4, json.size());
    /*

     */
  }

  @Test
  public void testCreateJsonArrayFromArray2() {
    Object[] numbers = new Integer[] {1, 2, 3};
    // Json is not immutable
    JsonArray json = new JsonArray(numbers);
    try {
      Iterator i = json.iterator();
      i.next();
      i.remove();
    } catch (UnsupportedOperationException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testContainsField() {
    JsonObject obj = new JsonObject().putString("s", "bar");
    assertTrue(obj.containsField("s"));
    assertFalse(obj.containsField("t"));
  }

  @Test
  // Strict JSON doesn't allow comments but we do so users can add comments to config files etc
  public void commentsInJSONTest() {
    String jsonWithComments =
      "// single line comment\n" +
      "/*\n" +
      "  This is a multi \n" +
      "  line comment\n" +
      "*/\n" +
      "{\n" +
      "// another single line comment this time inside the JSON object itself\n" +
      "  \"foo\": \"bar\" // and a single line comment at end of line \n" +
      "/*\n" +
      "  This is a another multi \n" +
      "  line comment this time inside the JSON object itself\n" +
      "*/\n" +
      "}";
    JsonObject json = new JsonObject(jsonWithComments);
    assertEquals("{\"foo\":\"bar\"}", json.encode());
  }

  @Test
  public void testCollectionsConstructor() {
    checktestCollectionsConstructor("bar", "bar");
    checktestCollectionsConstructor((byte) 3, (byte) 3);
    checktestCollectionsConstructor((short) 3, (short) 3);
    checktestCollectionsConstructor(3, 3);
    checktestCollectionsConstructor((long) 3, (long) 3);
    checktestCollectionsConstructor(new BigInteger("3"), (long) 3);
    checktestCollectionsConstructor((double) 3, (double) 3);
    checktestCollectionsConstructor((float) 3, (float) 3);
    checktestCollectionsConstructor(new BigDecimal(3), (double) 3);
    checktestCollectionsConstructor(true, true);
    checktestCollectionsConstructor(Collections.singletonMap("bar", "juu"), Collections.singletonMap("bar", "juu"));
    checktestCollectionsConstructor(new StringBuilder("some").append("thing"), "something");
    checkInvalid(Locale.CANADA);
  }

  private <T> void checktestCollectionsConstructor(T value, T expected) {
    JsonObject obj = new JsonObject(Collections.singletonMap("foo", value));
    Object actual = obj.toMap().get("foo");
    assertEquals(expected, actual);
    JsonArray array = new JsonArray(Collections.singletonList(value));
    assertEquals(Collections.singletonList(expected), array.toList());
  }

  private void checkInvalid(Object invalid) {
    try {
      new JsonObject(Collections.singletonMap("foo", invalid));
      fail();
    } catch (VertxException ignore) {
    }
    try {
      new JsonArray(Collections.singletonList(invalid));
      fail();
    } catch (VertxException ignore) {
    }
  }
}
