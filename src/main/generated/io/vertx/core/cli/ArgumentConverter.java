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

package io.vertx.core.cli;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.cli.Argument}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.cli.Argument} original class using Vert.x codegen.
 */
public class ArgumentConverter {

  public static void fromJson(JsonObject json, Argument obj) {
    if (json.getValue("argName") instanceof String) {
      obj.setArgName((String)json.getValue("argName"));
    }
    if (json.getValue("defaultValue") instanceof String) {
      obj.setDefaultValue((String)json.getValue("defaultValue"));
    }
    if (json.getValue("description") instanceof String) {
      obj.setDescription((String)json.getValue("description"));
    }
    if (json.getValue("hidden") instanceof Boolean) {
      obj.setHidden((Boolean)json.getValue("hidden"));
    }
    if (json.getValue("index") instanceof Number) {
      obj.setIndex(((Number)json.getValue("index")).intValue());
    }
    if (json.getValue("multiValued") instanceof Boolean) {
      obj.setMultiValued((Boolean)json.getValue("multiValued"));
    }
    if (json.getValue("required") instanceof Boolean) {
      obj.setRequired((Boolean)json.getValue("required"));
    }
  }

  public static void toJson(Argument obj, JsonObject json) {
    if (obj.getArgName() != null) {
      json.put("argName", obj.getArgName());
    }
    if (obj.getDefaultValue() != null) {
      json.put("defaultValue", obj.getDefaultValue());
    }
    if (obj.getDescription() != null) {
      json.put("description", obj.getDescription());
    }
    json.put("hidden", obj.isHidden());
    json.put("index", obj.getIndex());
    json.put("multiValued", obj.isMultiValued());
    json.put("required", obj.isRequired());
  }
}