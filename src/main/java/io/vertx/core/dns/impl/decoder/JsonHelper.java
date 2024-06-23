package io.vertx.core.dns.impl.decoder;

import io.vertx.codegen.Helper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonHelper {
  public static <T> T normalizePropertyNames(T obj) {
    if (obj instanceof JsonObject) {
      JsonObject json = new JsonObject();
      ((JsonObject) obj).forEach(member -> json.put(normalizePropertyName(member.getKey()), normalizePropertyNames(member.getValue())));
      return (T) json;
    }

    if (obj instanceof JsonArray) {
      JsonArray json = new JsonArray();
      ((JsonArray) obj).forEach(item -> json.add(normalizePropertyNames(item)));
      return (T) json;
    }

    return obj;
  }

  private static String normalizePropertyName(String text) {
    if(text == null || text.isEmpty())
      return text;
    return Helper.normalizePropertyName(text);
  }

  public static String appendDotIfRequired(String name) {
    if (!name.endsWith(".")) {
      name += ".";
    }
    return name;
  }

}
