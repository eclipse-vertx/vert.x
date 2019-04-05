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

package docoverride.json;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.docgen.Source;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tim on 09/01/15.
 */
@Source
public class Examples {

  public void example0_1() {
    String jsonString = "{\"foo\":\"bar\"}";
    JsonObject object = new JsonObject(jsonString);
  }

  public void exampleCreateFromMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("foo", "bar");
    map.put("xyz", 3);
    JsonObject object = new JsonObject(map);
  }

  public void example0_2() {
    String jsonString = "[\"foo\",\"bar\"]";
    JsonArray array = new JsonArray(jsonString);
  }

  public void example1() {
    JsonObject object = new JsonObject();
    object.put("foo", "bar").put("num", 123).put("mybool", true);
  }

  private static class User {
    User(String firstName, String lastName) {
    }
  }

  public void mapFromPojo(HttpServerRequest request) {
    User user = new User("Dale", "Cooper");
    JsonObject jsonObject = JsonObject.mapFrom(user);
  }

  public void mapToPojo(HttpServerRequest request) {
    request.bodyHandler(buff -> {
      JsonObject jsonObject = buff.toJsonObject();
      User javaObject = jsonObject.mapTo(User.class);
    });
  }

  public void example2(JsonObject jsonObject) {
    String val = jsonObject.getString("some-key");
    int intVal = jsonObject.getInteger("some-other-key");
  }

  public void example3() {
    JsonArray array = new JsonArray();
    array.add("foo").add(123).add(false);
  }

  public void example4(JsonArray array) {
    String val = array.getString(0);
    Integer intVal = array.getInteger(1);
    Boolean boolVal = array.getBoolean(2);
  }

  public void example1Pointers() {
    // Build a pointer from a string
    JsonPointer pointer1 = JsonPointer.from("/hello/world");
    // Build a pointer from an URI
    JsonPointer pointer2 = JsonPointer.fromURI(URI.create("#/hello/world"));
    // Build a pointer manually
    JsonPointer pointer3 = JsonPointer.create()
      .append("hello")
      .append("world");
  }

  public void example2Pointers(JsonPointer objectPointer, JsonObject jsonObject, JsonPointer arrayPointer, JsonArray jsonArray) {
    // Query a JsonObject
    Object result1 = objectPointer.queryJson(jsonObject);
    // Query a JsonArray
    Object result2 = arrayPointer.queryJson(jsonArray);
    // Write starting from a JsonObject
    objectPointer.writeJson(jsonObject, "new element");
    // Write starting from a JsonObject
    arrayPointer.writeJson(jsonArray, "new element");
  }

}
