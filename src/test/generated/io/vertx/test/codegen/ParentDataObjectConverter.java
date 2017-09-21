/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.test.codegen;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.test.codegen.ParentDataObject}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.test.codegen.ParentDataObject} original class using Vert.x codegen.
 */
public class ParentDataObjectConverter {

  public static void fromJson(JsonObject json, ParentDataObject obj) {
    if (json.getValue("parentProperty") instanceof String) {
      obj.setParentProperty((String)json.getValue("parentProperty"));
    }
  }

  public static void toJson(ParentDataObject obj, JsonObject json) {
    if (obj.getParentProperty() != null) {
      json.put("parentProperty", obj.getParentProperty());
    }
  }
}