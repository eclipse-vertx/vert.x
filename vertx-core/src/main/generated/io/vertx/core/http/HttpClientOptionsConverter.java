package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.core.http.HttpClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpClientOptions} original class using Vert.x codegen.
 */
public class HttpClientOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HttpClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "http2MultiplexingLimit":
          if (member.getValue() instanceof Number) {
            obj.setHttp2MultiplexingLimit(((Number)member.getValue()).intValue());
          }
          break;
        case "http2ConnectionWindowSize":
          if (member.getValue() instanceof Number) {
            obj.setHttp2ConnectionWindowSize(((Number)member.getValue()).intValue());
          }
          break;
        case "http2KeepAliveTimeout":
          if (member.getValue() instanceof Number) {
            obj.setHttp2KeepAliveTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "http2UpgradeMaxContentLength":
          if (member.getValue() instanceof Number) {
            obj.setHttp2UpgradeMaxContentLength(((Number)member.getValue()).intValue());
          }
          break;
        case "http2MultiplexImplementation":
          if (member.getValue() instanceof Boolean) {
            obj.setHttp2MultiplexImplementation((Boolean)member.getValue());
          }
          break;
        case "keepAlive":
          if (member.getValue() instanceof Boolean) {
            obj.setKeepAlive((Boolean)member.getValue());
          }
          break;
        case "keepAliveTimeout":
          if (member.getValue() instanceof Number) {
            obj.setKeepAliveTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "pipelining":
          if (member.getValue() instanceof Boolean) {
            obj.setPipelining((Boolean)member.getValue());
          }
          break;
        case "pipeliningLimit":
          if (member.getValue() instanceof Number) {
            obj.setPipeliningLimit(((Number)member.getValue()).intValue());
          }
          break;
        case "verifyHost":
          if (member.getValue() instanceof Boolean) {
            obj.setVerifyHost((Boolean)member.getValue());
          }
          break;
        case "decompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setDecompressionSupported((Boolean)member.getValue());
          }
          break;
        case "defaultHost":
          if (member.getValue() instanceof String) {
            obj.setDefaultHost((String)member.getValue());
          }
          break;
        case "defaultPort":
          if (member.getValue() instanceof Number) {
            obj.setDefaultPort(((Number)member.getValue()).intValue());
          }
          break;
        case "protocolVersion":
          if (member.getValue() instanceof String) {
            obj.setProtocolVersion(io.vertx.core.http.HttpVersion.valueOf((String)member.getValue()));
          }
          break;
        case "maxChunkSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxChunkSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxInitialLineLength":
          if (member.getValue() instanceof Number) {
            obj.setMaxInitialLineLength(((Number)member.getValue()).intValue());
          }
          break;
        case "maxHeaderSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxHeaderSize(((Number)member.getValue()).intValue());
          }
          break;
        case "initialSettings":
          if (member.getValue() instanceof JsonObject) {
            obj.setInitialSettings(new io.vertx.core.http.Http2Settings((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "initialHttp3Settings":
          if (member.getValue() instanceof JsonObject) {
            obj.setInitialHttp3Settings(new io.vertx.core.http.Http3Settings((io.vertx.core.json.JsonObject)member.getValue()));
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
        case "http2ClearTextUpgrade":
          if (member.getValue() instanceof Boolean) {
            obj.setHttp2ClearTextUpgrade((Boolean)member.getValue());
          }
          break;
        case "http2ClearTextUpgradeWithPreflightRequest":
          if (member.getValue() instanceof Boolean) {
            obj.setHttp2ClearTextUpgradeWithPreflightRequest((Boolean)member.getValue());
          }
          break;
        case "maxRedirects":
          if (member.getValue() instanceof Number) {
            obj.setMaxRedirects(((Number)member.getValue()).intValue());
          }
          break;
        case "forceSni":
          if (member.getValue() instanceof Boolean) {
            obj.setForceSni((Boolean)member.getValue());
          }
          break;
        case "decoderInitialBufferSize":
          if (member.getValue() instanceof Number) {
            obj.setDecoderInitialBufferSize(((Number)member.getValue()).intValue());
          }
          break;
        case "tracingPolicy":
          if (member.getValue() instanceof String) {
            obj.setTracingPolicy(io.vertx.core.tracing.TracingPolicy.valueOf((String)member.getValue()));
          }
          break;
        case "shared":
          if (member.getValue() instanceof Boolean) {
            obj.setShared((Boolean)member.getValue());
          }
          break;
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(HttpClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HttpClientOptions obj, java.util.Map<String, Object> json) {
    json.put("http2MultiplexingLimit", obj.getHttp2MultiplexingLimit());
    json.put("http2ConnectionWindowSize", obj.getHttp2ConnectionWindowSize());
    json.put("http2KeepAliveTimeout", obj.getHttp2KeepAliveTimeout());
    json.put("http2UpgradeMaxContentLength", obj.getHttp2UpgradeMaxContentLength());
    json.put("http2MultiplexImplementation", obj.getHttp2MultiplexImplementation());
    json.put("keepAlive", obj.isKeepAlive());
    json.put("keepAliveTimeout", obj.getKeepAliveTimeout());
    json.put("pipelining", obj.isPipelining());
    json.put("pipeliningLimit", obj.getPipeliningLimit());
    json.put("verifyHost", obj.isVerifyHost());
    json.put("decompressionSupported", obj.isDecompressionSupported());
    if (obj.getDefaultHost() != null) {
      json.put("defaultHost", obj.getDefaultHost());
    }
    json.put("defaultPort", obj.getDefaultPort());
    if (obj.getProtocolVersion() != null) {
      json.put("protocolVersion", obj.getProtocolVersion().name());
    }
    json.put("maxChunkSize", obj.getMaxChunkSize());
    json.put("maxInitialLineLength", obj.getMaxInitialLineLength());
    json.put("maxHeaderSize", obj.getMaxHeaderSize());
    if (obj.getInitialSettings() != null) {
      json.put("initialSettings", obj.getInitialSettings().toJson());
    }
    if (obj.getInitialHttp3Settings() != null) {
      json.put("initialHttp3Settings", obj.getInitialHttp3Settings().toJson());
    }
    if (obj.getAlpnVersions() != null) {
      JsonArray array = new JsonArray();
      obj.getAlpnVersions().forEach(item -> array.add(item.name()));
      json.put("alpnVersions", array);
    }
    json.put("http2ClearTextUpgrade", obj.isHttp2ClearTextUpgrade());
    json.put("http2ClearTextUpgradeWithPreflightRequest", obj.isHttp2ClearTextUpgradeWithPreflightRequest());
    json.put("maxRedirects", obj.getMaxRedirects());
    json.put("forceSni", obj.isForceSni());
    json.put("decoderInitialBufferSize", obj.getDecoderInitialBufferSize());
    if (obj.getTracingPolicy() != null) {
      json.put("tracingPolicy", obj.getTracingPolicy().name());
    }
    json.put("shared", obj.isShared());
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
  }
}
