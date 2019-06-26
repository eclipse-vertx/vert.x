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
package io.vertx.core.json;

import io.vertx.core.json.codecs.*;
import org.junit.Test;

import static io.vertx.core.json.Json.*;
import static org.junit.Assert.assertEquals;

public class JsonCodecLoaderTest {

  @Test
  public void booleanCodecTest() {
    MyBooleanPojo pojo = new MyBooleanPojo();
    pojo.setValue(true);
    assertEquals(true, mapFrom(pojo));
    assertEquals(pojo, mapTo(true, MyBooleanPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void booleanCodecWrongTypeTest() {
    decodeValue(encodeToBuffer("aaa"), MyBooleanPojo.class);
  }

  @Test
  public void doubleCodecTest() {
    MyDoublePojo pojo = new MyDoublePojo();
    pojo.setValue(1.2d);
    assertEquals(1.2d, mapFrom(pojo));
    assertEquals(pojo, mapTo(1.2d, MyDoublePojo.class));
  }

  @Test(expected = DecodeException.class)
  public void doubleCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(""), MyDoublePojo.class);
  }

  @Test
  public void floatCodecTest() {
    MyFloatPojo pojo = new MyFloatPojo();
    pojo.setValue(1.2f);
    assertEquals(1.2f, mapFrom(pojo));
    assertEquals(pojo, mapTo(1.2f, MyFloatPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void floatCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(""), MyFloatPojo.class);
  }

  @Test
  public void intCodecTest() {
    MyIntegerPojo pojo = new MyIntegerPojo();
    pojo.setValue(1);
    assertEquals(1, mapFrom(pojo));
    assertEquals(pojo, mapTo(1, MyIntegerPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void intCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(""), MyIntegerPojo.class);
  }

  @Test
  public void longCodecTest() {
    MyLongPojo pojo = new MyLongPojo();
    pojo.setValue(1L);
    assertEquals(1L, mapFrom(pojo));
    assertEquals(pojo, mapTo(1L, MyLongPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void longCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(""), MyLongPojo.class);
  }

  @Test
  public void shortCodecTest() {
    MyShortPojo pojo = new MyShortPojo();
    pojo.setValue((short)1);
    assertEquals((short)1, mapFrom(pojo));
    assertEquals(pojo, mapTo((short)1, MyShortPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void shortCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(""), MyShortPojo.class);
  }

  @Test
  public void jsonArrayCodecTest() {
    MyJsonArrayPojo pojo = new MyJsonArrayPojo();
    JsonArray array = new JsonArray().add(1).add(2).add(3);
    pojo.setValue(array);
    assertEquals(array, mapFrom(pojo));
    assertEquals(pojo, mapTo(array, MyJsonArrayPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void jsonArrayCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(2), MyJsonArrayPojo.class);
  }

  @Test
  public void jsonObjectCodecTest() {
    MyJsonObjectPojo pojo = new MyJsonObjectPojo();
    JsonObject obj = new JsonObject().put("a", 1).put("b", "c");
    pojo.setValue(obj);
    assertEquals(obj, mapFrom(pojo));
    assertEquals(pojo, mapTo(obj, MyJsonObjectPojo.class));
  }

  @Test(expected = DecodeException.class)
  public void jsonObjectCodecWrongTypeTest() {
    decodeValue(encodeToBuffer(2), MyJsonObjectPojo.class);
  }
  
}
