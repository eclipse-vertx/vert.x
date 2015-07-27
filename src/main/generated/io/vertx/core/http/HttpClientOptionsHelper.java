package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class HttpClientOptionsHelper {

  public static void fromJson(JsonObject json, HttpClientOptions obj) {
    if (json.getValue("defaultHost") instanceof String) {
      obj.setDefaultHost((String)json.getValue("defaultHost"));
    }
    if (json.getValue("defaultPort") instanceof Number) {
      obj.setDefaultPort(((Number)json.getValue("defaultPort")).intValue());
    }
    if (json.getValue("keepAlive") instanceof Boolean) {
      obj.setKeepAlive((Boolean)json.getValue("keepAlive"));
    }
    if (json.getValue("maxPoolSize") instanceof Number) {
      obj.setMaxPoolSize(((Number)json.getValue("maxPoolSize")).intValue());
    }
    if (json.getValue("maxWebsocketFrameSize") instanceof Number) {
      obj.setMaxWebsocketFrameSize(((Number)json.getValue("maxWebsocketFrameSize")).intValue());
    }
    if (json.getValue("pipelining") instanceof Boolean) {
      obj.setPipelining((Boolean)json.getValue("pipelining"));
    }
    if (json.getValue("tryUseCompression") instanceof Boolean) {
      obj.setTryUseCompression((Boolean)json.getValue("tryUseCompression"));
    }
    if (json.getValue("verifyHost") instanceof Boolean) {
      obj.setVerifyHost((Boolean)json.getValue("verifyHost"));
    }
  }

  public static void toJson(HttpClientOptions obj, JsonObject json) {
    if (obj.getDefaultHost() != null) {
      json.put("defaultHost", obj.getDefaultHost());
    }
    json.put("defaultPort", obj.getDefaultPort());
    json.put("keepAlive", obj.isKeepAlive());
    json.put("maxPoolSize", obj.getMaxPoolSize());
    json.put("maxWebsocketFrameSize", obj.getMaxWebsocketFrameSize());
    json.put("pipelining", obj.isPipelining());
    json.put("tryUseCompression", obj.isTryUseCompression());
    json.put("verifyHost", obj.isVerifyHost());
  }
}