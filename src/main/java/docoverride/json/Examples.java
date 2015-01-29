/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package docoverride.json;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.docgen.Source;

/**
 * Created by tim on 09/01/15.
 */
@Source
public class Examples {

  public void example0_1() {
    String jsonString = "{\"foo\":\"bar\"}";
    JsonObject object = new JsonObject(jsonString);
  }

  public void example0_2() {
    String jsonString = "[\"foo\",\"bar\"]";
    JsonArray array = new JsonArray(jsonString);
  }

  public void example1() {
    JsonObject object = new JsonObject();
    object.put("foo", "bar").put("num", 123).put("mybool", true);
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




}
