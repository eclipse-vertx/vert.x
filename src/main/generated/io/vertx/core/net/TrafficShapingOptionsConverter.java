package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.TrafficShapingOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.TrafficShapingOptions} original class using Vert.x codegen.
 */
public class TrafficShapingOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, TrafficShapingOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "checkIntervalForStats":
          if (member.getValue() instanceof Number) {
            obj.setCheckIntervalForStats(((Number)member.getValue()).longValue());
          }
          break;
        case "checkIntervalForStatsTimeUnit":
          if (member.getValue() instanceof String) {
            obj.setCheckIntervalForStatsTimeUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "inboundGlobalBandwidth":
          if (member.getValue() instanceof Number) {
            obj.setInboundGlobalBandwidth(((Number)member.getValue()).longValue());
          }
          break;
        case "maxDelayToWait":
          if (member.getValue() instanceof Number) {
            obj.setMaxDelayToWait(((Number)member.getValue()).longValue());
          }
          break;
        case "maxDelayToWaitTimeUnit":
          break;
        case "maxDelayToWaitUnit":
          if (member.getValue() instanceof String) {
            obj.setMaxDelayToWaitUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "outboundGlobalBandwidth":
          if (member.getValue() instanceof Number) {
            obj.setOutboundGlobalBandwidth(((Number)member.getValue()).longValue());
          }
          break;
        case "peakOutboundGlobalBandwidth":
          if (member.getValue() instanceof Number) {
            obj.setPeakOutboundGlobalBandwidth(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(TrafficShapingOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(TrafficShapingOptions obj, java.util.Map<String, Object> json) {
    json.put("checkIntervalForStats", obj.getCheckIntervalForStats());
    if (obj.getCheckIntervalForStatsTimeUnit() != null) {
      json.put("checkIntervalForStatsTimeUnit", obj.getCheckIntervalForStatsTimeUnit().name());
    }
    json.put("inboundGlobalBandwidth", obj.getInboundGlobalBandwidth());
    json.put("maxDelayToWait", obj.getMaxDelayToWait());
    if (obj.getMaxDelayToWaitTimeUnit() != null) {
      json.put("maxDelayToWaitTimeUnit", obj.getMaxDelayToWaitTimeUnit().name());
    }
    json.put("outboundGlobalBandwidth", obj.getOutboundGlobalBandwidth());
    json.put("peakOutboundGlobalBandwidth", obj.getPeakOutboundGlobalBandwidth());
  }
}
