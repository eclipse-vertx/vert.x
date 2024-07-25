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

package examples;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;

import java.net.URI;

public class JsonPointerExamples {

  public void example1Pointers() {
    // Build a pointer from a string
    JsonPointer pointer1 = JsonPointer.from("/hello/world");
    // Build a pointer manually
    JsonPointer pointer2 = JsonPointer.create()
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
