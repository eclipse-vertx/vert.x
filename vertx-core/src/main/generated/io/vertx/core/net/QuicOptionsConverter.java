package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.core.net.QuicOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.QuicOptions} original class using Vert.x codegen.
 */
public class QuicOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, QuicOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "http3InitialMaxStreamsBidirectional":
          if (member.getValue() instanceof Number) {
            obj.setHttp3InitialMaxStreamsBidirectional(((Number)member.getValue()).longValue());
          }
          break;
        case "http3InitialMaxData":
          if (member.getValue() instanceof Number) {
            obj.setHttp3InitialMaxData(((Number)member.getValue()).longValue());
          }
          break;
        case "http3InitialMaxStreamDataBidirectionalLocal":
          if (member.getValue() instanceof Number) {
            obj.setHttp3InitialMaxStreamDataBidirectionalLocal(((Number)member.getValue()).longValue());
          }
          break;
        case "http3InitialMaxStreamDataBidirectionalRemote":
          if (member.getValue() instanceof Number) {
            obj.setHttp3InitialMaxStreamDataBidirectionalRemote(((Number)member.getValue()).longValue());
          }
          break;
        case "http3InitialMaxStreamDataUnidirectional":
          if (member.getValue() instanceof Number) {
            obj.setHttp3InitialMaxStreamDataUnidirectional(((Number)member.getValue()).longValue());
          }
          break;
        case "http3InitialMaxStreamsUnidirectional":
          if (member.getValue() instanceof Number) {
            obj.setHttp3InitialMaxStreamsUnidirectional(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(QuicOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(QuicOptions obj, java.util.Map<String, Object> json) {
    json.put("http3InitialMaxStreamsBidirectional", obj.getHttp3InitialMaxStreamsBidirectional());
    json.put("http3InitialMaxData", obj.getHttp3InitialMaxData());
    json.put("http3InitialMaxStreamDataBidirectionalLocal", obj.getHttp3InitialMaxStreamDataBidirectionalLocal());
    json.put("http3InitialMaxStreamDataBidirectionalRemote", obj.getHttp3InitialMaxStreamDataBidirectionalRemote());
    json.put("http3InitialMaxStreamDataUnidirectional", obj.getHttp3InitialMaxStreamDataUnidirectional());
    json.put("http3InitialMaxStreamsUnidirectional", obj.getHttp3InitialMaxStreamsUnidirectional());
  }
}
