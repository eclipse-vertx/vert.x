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
package io.vertx.core.json;

import org.junit.Assert;
import org.junit.Test;

public class JsonTest {

  @Test
  public void testJsonArray() {
    JsonArray array = new JsonArray();
    array.add(1);
    array.add("a2");
    array.add(3f);
    String encoded = Json.encode(array);
    JsonArray decoded = Json.decodeValue(encoded, JsonArray.class);
    Assert.assertEquals(array, decoded);
  }

  @Test
  public void testJsonObject() {
    JsonObject object = new JsonObject();
    object.put("a", "a1");
    object.put("b", 1);
    object.put("c", 2f);
    String encoded = Json.encode(object);
    JsonObject decoded = Json.decodeValue(encoded, JsonObject.class);
    Assert.assertEquals(object, decoded);
  }

  @Test
  public void testJsonStructure() {
    JsonArray array = new JsonArray();
    array.add(1);
    array.add(2);
    array.add(3);

    JsonObject object = new JsonObject();
    object.put("a", "a1");
    object.put("b", 1);
    object.put("c", 2f);

    String arrayEncoded = Json.encode(array);
    String objectEncoded = Json.encode(object);

    JsonStructure structure1 = Json.decodeValue(arrayEncoded, JsonStructure.class);
    JsonStructure structure2 = Json.decodeValue(objectEncoded, JsonStructure.class);

    Assert.assertTrue(structure1.isArray());
    Assert.assertTrue(structure2.isObject());

    Assert.assertEquals(array, structure1);
    Assert.assertEquals(object, structure2);
  }

}
