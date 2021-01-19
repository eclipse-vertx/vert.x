package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.HttpServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpServerOptions} original class using Vert.x codegen.
 */
public class HttpServerOptionsConverter {


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
            obj.setInitialSettings(new io.vertx.core.http.Http2Settings((io.vertx.core.json.JsonObject)member.getValue()));
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
        case "maxWebSocketFrameSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxWebSocketFrameSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxWebSocketMessageSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxWebSocketMessageSize(((Number)member.getValue()).intValue());
          }
          break;
        case "perFrameWebSocketCompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setPerFrameWebSocketCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "perMessageWebSocketCompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setPerMessageWebSocketCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "tracingPolicy":
          if (member.getValue() instanceof String) {
            obj.setTracingPolicy(io.vertx.core.tracing.TracingPolicy.valueOf((String)member.getValue()));
          }
          break;
        case "webSocketAllowServerNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setWebSocketAllowServerNoContext((Boolean)member.getValue());
          }
          break;
        case "webSocketClosingTimeout":
          if (member.getValue() instanceof Number) {
            obj.setWebSocketClosingTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "webSocketCompressionLevel":
          if (member.getValue() instanceof Number) {
            obj.setWebSocketCompressionLevel(((Number)member.getValue()).intValue());
          }
          break;
        case "webSocketPreferredClientNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setWebSocketPreferredClientNoContext((Boolean)member.getValue());
          }
          break;
        case "webSocketSubProtocols":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setWebSocketSubProtocols(list);
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
    json.put("maxWebSocketFrameSize", obj.getMaxWebSocketFrameSize());
    json.put("maxWebSocketMessageSize", obj.getMaxWebSocketMessageSize());
    json.put("perFrameWebSocketCompressionSupported", obj.getPerFrameWebSocketCompressionSupported());
    json.put("perMessageWebSocketCompressionSupported", obj.getPerMessageWebSocketCompressionSupported());
    if (obj.getTracingPolicy() != null) {
      json.put("tracingPolicy", obj.getTracingPolicy().name());
    }
    json.put("webSocketAllowServerNoContext", obj.getWebSocketAllowServerNoContext());
    json.put("webSocketClosingTimeout", obj.getWebSocketClosingTimeout());
    json.put("webSocketCompressionLevel", obj.getWebSocketCompressionLevel());
    json.put("webSocketPreferredClientNoContext", obj.getWebSocketPreferredClientNoContext());
    if (obj.getWebSocketSubProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getWebSocketSubProtocols().forEach(item -> array.add(item));
      json.put("webSocketSubProtocols", array);
    }
  }
}
