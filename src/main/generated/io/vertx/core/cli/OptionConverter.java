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
 * Converter for {@link io.vertx.core.cli.Option}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.cli.Option} original class using Vert.x codegen.
 */
public class OptionConverter {

  public static void fromJson(JsonObject json, Option obj) {
    if (json.getValue("argName") instanceof String) {
      obj.setArgName((String)json.getValue("argName"));
    }
    if (json.getValue("choices") instanceof JsonArray) {
      json.getJsonArray("choices").forEach(item -> {
        if (item instanceof String)
          obj.addChoice((String)item);
      });
    }
    if (json.getValue("defaultValue") instanceof String) {
      obj.setDefaultValue((String)json.getValue("defaultValue"));
    }
    if (json.getValue("description") instanceof String) {
      obj.setDescription((String)json.getValue("description"));
    }
    if (json.getValue("flag") instanceof Boolean) {
      obj.setFlag((Boolean)json.getValue("flag"));
    }
    if (json.getValue("help") instanceof Boolean) {
      obj.setHelp((Boolean)json.getValue("help"));
    }
    if (json.getValue("hidden") instanceof Boolean) {
      obj.setHidden((Boolean)json.getValue("hidden"));
    }
    if (json.getValue("longName") instanceof String) {
      obj.setLongName((String)json.getValue("longName"));
    }
    if (json.getValue("multiValued") instanceof Boolean) {
      obj.setMultiValued((Boolean)json.getValue("multiValued"));
    }
    if (json.getValue("required") instanceof Boolean) {
      obj.setRequired((Boolean)json.getValue("required"));
    }
    if (json.getValue("shortName") instanceof String) {
      obj.setShortName((String)json.getValue("shortName"));
    }
    if (json.getValue("singleValued") instanceof Boolean) {
      obj.setSingleValued((Boolean)json.getValue("singleValued"));
    }
  }

  public static void toJson(Option obj, JsonObject json) {
    if (obj.getArgName() != null) {
      json.put("argName", obj.getArgName());
    }
    if (obj.getChoices() != null) {
      JsonArray array = new JsonArray();
      obj.getChoices().forEach(item -> array.add(item));
      json.put("choices", array);
    }
    if (obj.getDefaultValue() != null) {
      json.put("defaultValue", obj.getDefaultValue());
    }
    if (obj.getDescription() != null) {
      json.put("description", obj.getDescription());
    }
    json.put("flag", obj.isFlag());
    json.put("help", obj.isHelp());
    json.put("hidden", obj.isHidden());
    if (obj.getLongName() != null) {
      json.put("longName", obj.getLongName());
    }
    json.put("multiValued", obj.isMultiValued());
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    json.put("required", obj.isRequired());
    if (obj.getShortName() != null) {
      json.put("shortName", obj.getShortName());
    }
    json.put("singleValued", obj.isSingleValued());
  }
}
