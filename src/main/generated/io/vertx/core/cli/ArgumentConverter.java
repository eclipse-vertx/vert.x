/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
