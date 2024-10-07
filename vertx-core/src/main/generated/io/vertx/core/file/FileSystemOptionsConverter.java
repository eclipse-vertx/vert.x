package io.vertx.core.file;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.file.FileSystemOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.FileSystemOptions} original class using Vert.x codegen.
 */
public class FileSystemOptionsConverter {

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
