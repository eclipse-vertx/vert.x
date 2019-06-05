package io.vertx.core.file;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.file.FileSystemOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.FileSystemOptions} original class using Vert.x codegen.
 */
public class FileSystemOptionsConverter implements JsonCodec<FileSystemOptions, JsonObject> {

  public static final FileSystemOptionsConverter INSTANCE = new FileSystemOptionsConverter();

  @Override public JsonObject encode(FileSystemOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public FileSystemOptions decode(JsonObject value) { return (value != null) ? new FileSystemOptions(value) : null; }

  @Override public Class<FileSystemOptions> getTargetClass() { return FileSystemOptions.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, FileSystemOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "classPathResolvingEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setClassPathResolvingEnabled((Boolean)member.getValue());
          }
          break;
        case "fileCacheDir":
          if (member.getValue() instanceof String) {
            obj.setFileCacheDir((String)member.getValue());
          }
          break;
        case "fileCachingEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setFileCachingEnabled((Boolean)member.getValue());
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
    if (obj.getFileCacheDir() != null) {
      json.put("fileCacheDir", obj.getFileCacheDir());
    }
    json.put("fileCachingEnabled", obj.isFileCachingEnabled());
  }
}
