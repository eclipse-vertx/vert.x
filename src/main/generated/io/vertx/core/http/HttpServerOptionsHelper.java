package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class HttpServerOptionsHelper {

  public static void fromJson(JsonObject json, HttpServerOptions obj) {
    if (json.getValue("compressionSupported") instanceof Boolean) {
      obj.setCompressionSupported((Boolean)json.getValue("compressionSupported"));
    }
    if (json.getValue("handle100ContinueAutomatically") instanceof Boolean) {
      obj.setHandle100ContinueAutomatically((Boolean)json.getValue("handle100ContinueAutomatically"));
    }
    if (json.getValue("maxWebsocketFrameSize") instanceof Number) {
      obj.setMaxWebsocketFrameSize(((Number)json.getValue("maxWebsocketFrameSize")).intValue());
    }
    if (json.getValue("websocketSubProtocols") instanceof String) {
      obj.setWebsocketSubProtocols((String)json.getValue("websocketSubProtocols"));
    }
  }

  public static void toJson(HttpServerOptions obj, JsonObject json) {
    json.put("compressionSupported", obj.isCompressionSupported());
    json.put("handle100ContinueAutomatically", obj.isHandle100ContinueAutomatically());
    json.put("maxWebsocketFrameSize", obj.getMaxWebsocketFrameSize());
    if (obj.getWebsocketSubProtocols() != null) {
      json.put("websocketSubProtocols", obj.getWebsocketSubProtocols());
    }
  }
}