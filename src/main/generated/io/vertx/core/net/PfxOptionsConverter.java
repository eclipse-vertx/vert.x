package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.net.PfxOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PfxOptions} original class using Vert.x codegen.
 */
public class PfxOptionsConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PfxOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
      }
    }
  }

   static void toJson(PfxOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PfxOptions obj, java.util.Map<String, Object> json) {
  }
}
