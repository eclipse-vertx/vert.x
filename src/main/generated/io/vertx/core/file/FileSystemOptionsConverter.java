package io.vertx.core.file;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.file.FileSystemOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.FileSystemOptions} original class using Vert.x codegen.
 */
public class FileSystemOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, FileSystemOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "classPathResolvingEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setClassPathResolvingEnabled((Boolean)member.getValue());
          }
          break;
        case "fileCachingEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setFileCachingEnabled((Boolean)member.getValue());
          }
          break;
        case "fileCacheDir":
          if (member.getValue() instanceof String) {
            obj.setFileCacheDir((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(FileSystemOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(FileSystemOptions obj, java.util.Map<String, Object> json) {
    json.put("classPathResolvingEnabled", obj.isClassPathResolvingEnabled());
    json.put("fileCachingEnabled", obj.isFileCachingEnabled());
    if (obj.getFileCacheDir() != null) {
      json.put("fileCacheDir", obj.getFileCacheDir());
    }
  }
}
