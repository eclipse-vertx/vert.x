package io.vertx.core.file;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.file.OpenOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.OpenOptions} original class using Vert.x codegen.
 */
public class OpenOptionsConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, OpenOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "append":
          if (member.getValue() instanceof Boolean) {
            obj.setAppend((Boolean)member.getValue());
          }
          break;
        case "create":
          if (member.getValue() instanceof Boolean) {
            obj.setCreate((Boolean)member.getValue());
          }
          break;
        case "createNew":
          if (member.getValue() instanceof Boolean) {
            obj.setCreateNew((Boolean)member.getValue());
          }
          break;
        case "deleteOnClose":
          if (member.getValue() instanceof Boolean) {
            obj.setDeleteOnClose((Boolean)member.getValue());
          }
          break;
        case "dsync":
          if (member.getValue() instanceof Boolean) {
            obj.setDsync((Boolean)member.getValue());
          }
          break;
        case "perms":
          if (member.getValue() instanceof String) {
            obj.setPerms((String)member.getValue());
          }
          break;
        case "read":
          if (member.getValue() instanceof Boolean) {
            obj.setRead((Boolean)member.getValue());
          }
          break;
        case "sparse":
          if (member.getValue() instanceof Boolean) {
            obj.setSparse((Boolean)member.getValue());
          }
          break;
        case "sync":
          if (member.getValue() instanceof Boolean) {
            obj.setSync((Boolean)member.getValue());
          }
          break;
        case "truncateExisting":
          if (member.getValue() instanceof Boolean) {
            obj.setTruncateExisting((Boolean)member.getValue());
          }
          break;
        case "write":
          if (member.getValue() instanceof Boolean) {
            obj.setWrite((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(OpenOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(OpenOptions obj, java.util.Map<String, Object> json) {
    json.put("append", obj.isAppend());
    json.put("create", obj.isCreate());
    json.put("createNew", obj.isCreateNew());
    json.put("deleteOnClose", obj.isDeleteOnClose());
    json.put("dsync", obj.isDsync());
    if (obj.getPerms() != null) {
      json.put("perms", obj.getPerms());
    }
    json.put("read", obj.isRead());
    json.put("sparse", obj.isSparse());
    json.put("sync", obj.isSync());
    json.put("truncateExisting", obj.isTruncateExisting());
    json.put("write", obj.isWrite());
  }
}
