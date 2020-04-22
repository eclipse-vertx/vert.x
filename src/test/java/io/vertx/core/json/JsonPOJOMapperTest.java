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

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author <a href="https://github.com/lukehutch">Luke Hutchison</a>
 */
public class JsonPOJOMapperTest {

  public static class MyType {
    public int a;
    public String b;
    public HashMap<String, Object> c = new HashMap<>();
    public List<MyType> d = new ArrayList<>();
    public List<Integer> e = new ArrayList<>();
  }

  @Test
  public void testSerialization() {
    MyType myObj0 = new MyType() {{
      a = -1;
      b = "obj0";
      c.put("z", Arrays.asList(7, 8));
      e.add(9);
    }};
    MyType myObj1 = new MyType() {{
      a = 5;
      b = "obj1";
      c.put("x", "1");
      c.put("y", 2);
      d.add(myObj0);
      e.add(3);
    }};

    JsonObject jsonObject1 = JsonObject.mapFrom(myObj1);
    String jsonStr1 = jsonObject1.encode();
    assertEquals("{\"a\":5,\"b\":\"obj1\",\"c\":{\"x\":\"1\",\"y\":2},\"d\":["
        +"{\"a\":-1,\"b\":\"obj0\",\"c\":{\"z\":[7,8]},\"d\":[],\"e\":[9]}"
        + "],\"e\":[3]}", jsonStr1);

    MyType myObj1Roundtrip = jsonObject1.mapTo(MyType.class);
    assertEquals(myObj1Roundtrip.a, 5);
    assertEquals(myObj1Roundtrip.b, "obj1");
    assertEquals(myObj1Roundtrip.c.get("x"), "1");
    assertEquals(myObj1Roundtrip.c.get("y"), new Integer(2));
    assertEquals(myObj1Roundtrip.e, Arrays.asList(3));
    MyType myObj0Roundtrip = myObj1Roundtrip.d.get(0);
    assertEquals(myObj0Roundtrip.a, -1);
    assertEquals(myObj0Roundtrip.b, "obj0");
    assertEquals(myObj0Roundtrip.c.get("z"), Arrays.asList(7, 8));
    assertEquals(myObj0Roundtrip.e, Arrays.asList(9));

    boolean caughtCycle = false;
    try {
      myObj0.d.add(myObj0);
      JsonObject.mapFrom(myObj0);
    } catch (IllegalArgumentException e) {
      caughtCycle = true;
    }
    if (!caughtCycle) {
      fail();
    }
  }

  public static class MyType2 {
    public Instant isodate = Instant.now();
    public byte[] base64 = "Hello World!".getBytes();
  }

  public static class MyType3 {
    public Instant isodate = Instant.now();
    public Buffer base64 = Buffer.buffer("Hello World!");
  }

  @Test
  public void testInstantFromPOJO() {
    JsonObject json = JsonObject.mapFrom(new MyType2());
    // attempt to deserialize back to a instant, asserting for not null
    // already means that there was an attempt to parse a string to instant
    // and that the parsing succeeded (the object is of type instant and not null)
    assertNotNull(json.getInstant("isodate"));
  }

  @Test
  public void testInstantToPOJO() {
    MyType2 obj = new JsonObject().put("isodate", Instant.EPOCH).mapTo(MyType2.class);
    assertEquals(Instant.EPOCH, obj.isodate);
  }

  @Test
  public void testInvalidInstantToPOJO() {
    testInvalidValueToPOJO("isodate");
  }

  @Test
  public void testBase64FromPOJO() {
    JsonObject json = JsonObject.mapFrom(new MyType2());
    // attempt to deserialize back to a byte[], asserting for not null
    // already means that there was an attempt to parse a string to byte[]
    // and that the parsing succeeded (the object is of type byte[] and not null)
    assertNotNull(json.getBinary("base64"));
    // same applies to Buffer
    assertNotNull(json.getBuffer("base64"));
  }

  @Test
  public void testBase64ToPOJO() {
    MyType2 obj = new JsonObject().put("base64", "Hello World!".getBytes()).mapTo(MyType2.class);
    assertArrayEquals("Hello World!".getBytes(), obj.base64);
    // same test but with Buffers
    MyType3 obj2 = new JsonObject().put("base64", "Hello World!".getBytes()).mapTo(MyType3.class);
    assertArrayEquals("Hello World!".getBytes(), obj.base64);
  }

  @Test
  public void testInvalidBase64ToPOJO() {
    testInvalidValueToPOJO("base64");
  }

  private void testInvalidValueToPOJO(String key) {
    try {
      new JsonObject().put(key, "1").mapTo(MyType2.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause(), is(instanceOf(InvalidFormatException.class)));
      InvalidFormatException ife = (InvalidFormatException) e.getCause();
      assertEquals("1", ife.getValue());
    }
  }

  @Test
  public void testNullPOJO() {
    assertNull(JsonObject.mapFrom(null));
  }

}
