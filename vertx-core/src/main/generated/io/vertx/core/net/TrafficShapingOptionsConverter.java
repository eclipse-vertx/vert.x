package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.net.TrafficShapingOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.TrafficShapingOptions} original class using Vert.x codegen.
 */
public class TrafficShapingOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, TrafficShapingOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "inboundGlobalBandwidth":
          if (member.getValue() instanceof Number) {
            obj.setInboundGlobalBandwidth(((Number)member.getValue()).longValue());
          }
          break;
        case "outboundGlobalBandwidth":
          if (member.getValue() instanceof Number) {
            obj.setOutboundGlobalBandwidth(((Number)member.getValue()).longValue());
          }
          break;
        case "maxDelayToWait":
          if (member.getValue() instanceof Number) {
            obj.setMaxDelayToWait(((Number)member.getValue()).longValue());
          }
          break;
        case "maxDelayToWaitUnit":
          if (member.getValue() instanceof String) {
            obj.setMaxDelayToWaitUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
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
        case "peakOutboundGlobalBandwidth":
          if (member.getValue() instanceof Number) {
            obj.setPeakOutboundGlobalBandwidth(((Number)member.getValue()).longValue());
          }
          break;
        case "maxDelayToWaitTimeUnit":
          break;
      }
    }
  }

   static void toJson(TrafficShapingOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(TrafficShapingOptions obj, java.util.Map<String, Object> json) {
    json.put("inboundGlobalBandwidth", obj.getInboundGlobalBandwidth());
    json.put("outboundGlobalBandwidth", obj.getOutboundGlobalBandwidth());
    json.put("maxDelayToWait", obj.getMaxDelayToWait());
    json.put("checkIntervalForStats", obj.getCheckIntervalForStats());
    if (obj.getCheckIntervalForStatsTimeUnit() != null) {
      json.put("checkIntervalForStatsTimeUnit", obj.getCheckIntervalForStatsTimeUnit().name());
    }
    json.put("peakOutboundGlobalBandwidth", obj.getPeakOutboundGlobalBandwidth());
    if (obj.getMaxDelayToWaitTimeUnit() != null) {
      json.put("maxDelayToWaitTimeUnit", obj.getMaxDelayToWaitTimeUnit().name());
    }
  }
}
