package io.vertx.core.cli;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.cli.Argument}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.cli.Argument} original class using Vert.x codegen.
 */
 class ArgumentConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Argument obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "argName":
          if (member.getValue() instanceof String) {
            obj.setArgName((String)member.getValue());
          }
          break;
        case "defaultValue":
          if (member.getValue() instanceof String) {
            obj.setDefaultValue((String)member.getValue());
          }
          break;
        case "description":
          if (member.getValue() instanceof String) {
            obj.setDescription((String)member.getValue());
          }
          break;
        case "hidden":
          if (member.getValue() instanceof Boolean) {
            obj.setHidden((Boolean)member.getValue());
          }
          break;
        case "index":
          if (member.getValue() instanceof Number) {
            obj.setIndex(((Number)member.getValue()).intValue());
          }
          break;
        case "multiValued":
          if (member.getValue() instanceof Boolean) {
            obj.setMultiValued((Boolean)member.getValue());
          }
          break;
        case "required":
          if (member.getValue() instanceof Boolean) {
            obj.setRequired((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(Argument obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(Argument obj, java.util.Map<String, Object> json) {
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
