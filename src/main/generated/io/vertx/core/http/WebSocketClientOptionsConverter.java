package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.http.WebSocketClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.WebSocketClientOptions} original class using Vert.x codegen.
 */
public class WebSocketClientOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, WebSocketClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "closingTimeout":
          if (member.getValue() instanceof Number) {
            obj.setClosingTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "compressionAllowClientNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setCompressionAllowClientNoContext((Boolean)member.getValue());
          }
          break;
        case "compressionLevel":
          if (member.getValue() instanceof Number) {
            obj.setCompressionLevel(((Number)member.getValue()).intValue());
          }
          break;
        case "compressionRequestServerNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setCompressionRequestServerNoContext((Boolean)member.getValue());
          }
          break;
        case "maxConnections":
          if (member.getValue() instanceof Number) {
            obj.setMaxConnections(((Number)member.getValue()).intValue());
          }
          break;
        case "maxFrameSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxFrameSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxMessageSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxMessageSize(((Number)member.getValue()).intValue());
          }
          break;
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
        case "sendUnmaskedFrames":
          if (member.getValue() instanceof Boolean) {
            obj.setSendUnmaskedFrames((Boolean)member.getValue());
          }
          break;
        case "shared":
          if (member.getValue() instanceof Boolean) {
            obj.setShared((Boolean)member.getValue());
          }
          break;
        case "tryUsePerFrameCompression":
          if (member.getValue() instanceof Boolean) {
            obj.setTryUsePerFrameCompression((Boolean)member.getValue());
          }
          break;
        case "tryUsePerMessageCompression":
          if (member.getValue() instanceof Boolean) {
            obj.setTryUsePerMessageCompression((Boolean)member.getValue());
          }
          break;
        case "verifyHost":
          if (member.getValue() instanceof Boolean) {
            obj.setVerifyHost((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(WebSocketClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(WebSocketClientOptions obj, java.util.Map<String, Object> json) {
    json.put("closingTimeout", obj.getClosingTimeout());
    json.put("compressionAllowClientNoContext", obj.getCompressionAllowClientNoContext());
    json.put("compressionLevel", obj.getCompressionLevel());
    json.put("compressionRequestServerNoContext", obj.getCompressionRequestServerNoContext());
    json.put("maxConnections", obj.getMaxConnections());
    json.put("maxFrameSize", obj.getMaxFrameSize());
    json.put("maxMessageSize", obj.getMaxMessageSize());
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    json.put("sendUnmaskedFrames", obj.isSendUnmaskedFrames());
    json.put("shared", obj.isShared());
    json.put("tryUsePerFrameCompression", obj.getTryUsePerFrameCompression());
    json.put("tryUsePerMessageCompression", obj.getTryUsePerMessageCompression());
    json.put("verifyHost", obj.isVerifyHost());
  }
}
