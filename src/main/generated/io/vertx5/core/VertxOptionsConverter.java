package io.vertx5.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx5.core.VertxOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx5.core.VertxOptions} original class using Vert.x codegen.
 */
public class VertxOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, VertxOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "addressResolverOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "disableTCCL":
          if (member.getValue() instanceof Boolean) {
            obj.setDisableTCCL((Boolean)member.getValue());
          }
          break;
        case "eventLoopPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setEventLoopPoolSize(((Number)member.getValue()).intValue());
          }
          break;
        case "fileSystemOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setFileSystemOptions(new io.vertx.core.file.FileSystemOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "internalBlockingPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setInternalBlockingPoolSize(((Number)member.getValue()).intValue());
          }
          break;
        case "tracingOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setTracingOptions(new io.vertx.core.tracing.TracingOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "workerPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setWorkerPoolSize(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(VertxOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(VertxOptions obj, java.util.Map<String, Object> json) {
    if (obj.getAddressResolverOptions() != null) {
      json.put("addressResolverOptions", obj.getAddressResolverOptions().toJson());
    }
    json.put("disableTCCL", obj.getDisableTCCL());
    json.put("eventLoopPoolSize", obj.getEventLoopPoolSize());
    if (obj.getFileSystemOptions() != null) {
      json.put("fileSystemOptions", obj.getFileSystemOptions().toJson());
    }
    json.put("internalBlockingPoolSize", obj.getInternalBlockingPoolSize());
    if (obj.getTracingOptions() != null) {
      json.put("tracingOptions", obj.getTracingOptions().toJson());
    }
    json.put("workerPoolSize", obj.getWorkerPoolSize());
  }
}
