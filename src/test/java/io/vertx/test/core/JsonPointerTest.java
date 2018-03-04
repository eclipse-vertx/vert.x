package io.vertx.test.core;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonPointer;
import io.vertx.core.json.impl.JsonPointerImpl;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
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
    assertEquals("/hello/world", pointer.build());
  }

  @Test
  public void testEncodingParsing() {
    List<String> keys = new ArrayList<>();
    keys.add("");
    keys.add("hell/o");
    keys.add("worl~d");
    JsonPointer pointer = new JsonPointerImpl(keys);
    assertEquals("/hell~1o/worl~0d", pointer.build());
  }

  @Test
  public void testURIParsing() {
    JsonPointer pointer = JsonPointer.fromURI("#/hello/world");
    assertEquals("/hello/world", pointer.build());
  }

  @Test
  public void testBuilding() {
    List<String> keys = new ArrayList<>();
    keys.add("");
    keys.add("hello");
    keys.add("world");
    JsonPointer pointer = new JsonPointerImpl(keys);
    assertEquals("/hello/world", pointer.build());
  }

  @Test
  public void testURIBuilding() {
    JsonPointer pointer = JsonPointer.create().append("hello").append("world");
    assertEquals(URI.create("#/hello/world"), URI.create(pointer.buildURI()));
  }

  @Test
  public void testEmptyBuilding() {
    JsonPointer pointer = JsonPointer.create();
    assertEquals("", pointer.build());
    assertEquals(URI.create("#"), URI.create(pointer.buildURI()));
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
    assertEquals(1, pointer.query(obj));
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
    assertEquals(1, JsonPointer.from("1/hello/world").query(array));
    assertEquals(1, JsonPointer.from("/1/hello/world").query(array));
    assertEquals(1, JsonPointer.fromURI("#1/hello/world").query(array));
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

    assertEquals(array, pointer.query(array));
    assertEquals(obj, pointer.query(obj));
    assertEquals("hello", pointer.query("hello"));
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
    assertNull(pointer.query(array));
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

    assertEquals(obj, JsonPointer.from("").query(obj));
    assertEquals(obj.getJsonArray("foo"), JsonPointer.from("/foo").query(obj));
    assertEquals(obj.getJsonArray("foo").getString(0), JsonPointer.from("/foo/0").query(obj));
    assertEquals(obj.getInteger(""), JsonPointer.from("/").query(obj));
    assertEquals(obj.getInteger("a/b"), JsonPointer.from("/a~1b").query(obj));
    assertEquals(obj.getInteger("c%d"), JsonPointer.from("/c%d").query(obj));
    assertEquals(obj.getInteger("e^f"), JsonPointer.from("/e^f" ).query(obj));
    assertEquals(obj.getInteger("g|h"), JsonPointer.from("/g|h").query(obj));
    assertEquals(obj.getInteger("i\\\\j"), JsonPointer.from("/i\\\\j").query(obj));
    assertEquals(obj.getInteger("k\\\"l"), JsonPointer.from("/k\\\"l").query(obj));
    assertEquals(obj.getInteger(" "), JsonPointer.from("/ ").query(obj));
    assertEquals(obj.getInteger("m~n"), JsonPointer.from("/m~0n").query(obj));
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
    assertTrue(JsonPointer.from("/hello/francesco").writeObject(obj, toInsert));
    assertEquals(toInsert, JsonPointer.from("/hello/francesco").query(obj));
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
    assertTrue(JsonPointer.from("/hello/world").writeObject(obj, toInsert));
    assertEquals(toInsert, JsonPointer.from("/hello/world").query(obj));
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
    assertTrue(JsonPointer.from("0/hello/world/francesco").writeArray(array, toInsert));
    assertEquals(toInsert, JsonPointer.from("0/hello/world/francesco").query(array));
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
    assertTrue(JsonPointer.from("-").writeArray(array, toInsert));
    assertEquals(toInsert, JsonPointer.from("2").query(array));
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
    assertTrue(JsonPointer.from("0").writeArray(array, toInsert));
    assertEquals(toInsert, JsonPointer.from("0").query(array));
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
    assertTrue(JsonPointer.from("/array/0").writeObject(root, toInsert));
    assertEquals(toInsert, JsonPointer.from("/array/0").query(root));
  }

  @Test(expected = IllegalStateException.class)
  public void testIllegalUsageOfWriteJsonArray() {
    JsonArray array = new JsonArray();
    JsonPointer.create().writeArray(array, new JsonObject());
  }

  @Test(expected = IllegalStateException.class)
  public void testIllegalUsageOfWrite() {
    JsonObject object = new JsonObject();
    JsonPointer.create().writeObject(object, new JsonArray());
  }

}
