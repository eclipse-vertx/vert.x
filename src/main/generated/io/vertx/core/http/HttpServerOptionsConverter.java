package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.http.HttpServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpServerOptions} original class using Vert.x codegen.
 */
 class HttpServerOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HttpServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "acceptUnmaskedFrames":
          if (member.getValue() instanceof Boolean) {
            obj.setAcceptUnmaskedFrames((Boolean)member.getValue());
          }
          break;
        case "alpnVersions":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.http.HttpVersion> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add(io.vertx.core.http.HttpVersion.valueOf((String)item));
            });
            obj.setAlpnVersions(list);
          }
          break;
        case "compressionLevel":
          if (member.getValue() instanceof Number) {
            obj.setCompressionLevel(((Number)member.getValue()).intValue());
          }
          break;
        case "compressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "decoderInitialBufferSize":
          if (member.getValue() instanceof Number) {
            obj.setDecoderInitialBufferSize(((Number)member.getValue()).intValue());
          }
          break;
        case "decompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setDecompressionSupported((Boolean)member.getValue());
          }
          break;
        case "handle100ContinueAutomatically":
          if (member.getValue() instanceof Boolean) {
            obj.setHandle100ContinueAutomatically((Boolean)member.getValue());
          }
          break;
        case "http2ConnectionWindowSize":
          if (member.getValue() instanceof Number) {
            obj.setHttp2ConnectionWindowSize(((Number)member.getValue()).intValue());
          }
          break;
        case "initialSettings":
          if (member.getValue() instanceof JsonObject) {
            obj.setInitialSettings(new io.vertx.core.http.Http2Settings((JsonObject)member.getValue()));
          }
          break;
        case "maxChunkSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxChunkSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxHeaderSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxHeaderSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxInitialLineLength":
          if (member.getValue() instanceof Number) {
            obj.setMaxInitialLineLength(((Number)member.getValue()).intValue());
          }
          break;
        case "maxWebsocketFrameSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxWebsocketFrameSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxWebsocketMessageSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxWebsocketMessageSize(((Number)member.getValue()).intValue());
          }
          break;
        case "perFrameWebsocketCompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setPerFrameWebsocketCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "perMessageWebsocketCompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setPerMessageWebsocketCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "websocketAllowServerNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setWebsocketAllowServerNoContext((Boolean)member.getValue());
          }
          break;
        case "websocketCompressionLevel":
          if (member.getValue() instanceof Number) {
            obj.setWebsocketCompressionLevel(((Number)member.getValue()).intValue());
          }
          break;
        case "websocketPreferredClientNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setWebsocketPreferredClientNoContext((Boolean)member.getValue());
          }
          break;
        case "websocketSubProtocols":
          if (member.getValue() instanceof String) {
            obj.setWebsocketSubProtocols((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(HttpServerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HttpServerOptions obj, java.util.Map<String, Object> json) {
    json.put("acceptUnmaskedFrames", obj.isAcceptUnmaskedFrames());
    if (obj.getAlpnVersions() != null) {
      JsonArray array = new JsonArray();
      obj.getAlpnVersions().forEach(item -> array.add(item.name()));
      json.put("alpnVersions", array);
    }
    json.put("compressionLevel", obj.getCompressionLevel());
    json.put("compressionSupported", obj.isCompressionSupported());
    json.put("decoderInitialBufferSize", obj.getDecoderInitialBufferSize());
    json.put("decompressionSupported", obj.isDecompressionSupported());
    json.put("handle100ContinueAutomatically", obj.isHandle100ContinueAutomatically());
    json.put("http2ConnectionWindowSize", obj.getHttp2ConnectionWindowSize());
    if (obj.getInitialSettings() != null) {
      json.put("initialSettings", obj.getInitialSettings().toJson());
    }
    json.put("maxChunkSize", obj.getMaxChunkSize());
    json.put("maxHeaderSize", obj.getMaxHeaderSize());
    json.put("maxInitialLineLength", obj.getMaxInitialLineLength());
    json.put("maxWebsocketFrameSize", obj.getMaxWebsocketFrameSize());
    json.put("maxWebsocketMessageSize", obj.getMaxWebsocketMessageSize());
    json.put("websocketAllowServerNoContext", obj.getWebsocketAllowServerNoContext());
    json.put("websocketPreferredClientNoContext", obj.getWebsocketPreferredClientNoContext());
    if (obj.getWebsocketSubProtocols() != null) {
      json.put("websocketSubProtocols", obj.getWebsocketSubProtocols());
    }
  }
}
