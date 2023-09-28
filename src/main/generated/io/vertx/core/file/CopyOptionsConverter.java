package io.vertx.core.file;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.file.CopyOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.CopyOptions} original class using Vert.x codegen.
 */
public class CopyOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, CopyOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "replaceExisting":
          if (member.getValue() instanceof Boolean) {
            obj.setReplaceExisting((Boolean)member.getValue());
          }
          break;
        case "copyAttributes":
          if (member.getValue() instanceof Boolean) {
            obj.setCopyAttributes((Boolean)member.getValue());
          }
          break;
        case "atomicMove":
          if (member.getValue() instanceof Boolean) {
            obj.setAtomicMove((Boolean)member.getValue());
          }
          break;
        case "nofollowLinks":
          if (member.getValue() instanceof Boolean) {
            obj.setNofollowLinks((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(CopyOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(CopyOptions obj, java.util.Map<String, Object> json) {
    json.put("replaceExisting", obj.isReplaceExisting());
    json.put("copyAttributes", obj.isCopyAttributes());
    json.put("atomicMove", obj.isAtomicMove());
    json.put("nofollowLinks", obj.isNofollowLinks());
  }
}
