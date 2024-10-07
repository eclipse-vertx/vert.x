package io.vertx.core.dns;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.dns.AddressResolverOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.AddressResolverOptions} original class using Vert.x codegen.
 */
public class AddressResolverOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, AddressResolverOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "hostsPath":
          if (member.getValue() instanceof String) {
            obj.setHostsPath((String)member.getValue());
          }
          break;
        case "hostsValue":
          if (member.getValue() instanceof String) {
            obj.setHostsValue(io.vertx.core.buffer.Buffer.fromJson((String)member.getValue()));
          }
          break;
        case "hostsRefreshPeriod":
          if (member.getValue() instanceof Number) {
            obj.setHostsRefreshPeriod(((Number)member.getValue()).intValue());
          }
          break;
        case "servers":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setServers(list);
          }
          break;
        case "optResourceEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setOptResourceEnabled((Boolean)member.getValue());
          }
          break;
        case "cacheMinTimeToLive":
          if (member.getValue() instanceof Number) {
            obj.setCacheMinTimeToLive(((Number)member.getValue()).intValue());
          }
          break;
        case "cacheMaxTimeToLive":
          if (member.getValue() instanceof Number) {
            obj.setCacheMaxTimeToLive(((Number)member.getValue()).intValue());
          }
          break;
        case "cacheNegativeTimeToLive":
          if (member.getValue() instanceof Number) {
            obj.setCacheNegativeTimeToLive(((Number)member.getValue()).intValue());
          }
          break;
        case "queryTimeout":
          if (member.getValue() instanceof Number) {
            obj.setQueryTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "maxQueries":
          if (member.getValue() instanceof Number) {
            obj.setMaxQueries(((Number)member.getValue()).intValue());
          }
          break;
        case "rdFlag":
          if (member.getValue() instanceof Boolean) {
            obj.setRdFlag((Boolean)member.getValue());
          }
          break;
        case "searchDomains":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setSearchDomains(list);
          }
          break;
        case "ndots":
          if (member.getValue() instanceof Number) {
            obj.setNdots(((Number)member.getValue()).intValue());
          }
          break;
        case "rotateServers":
          if (member.getValue() instanceof Boolean) {
            obj.setRotateServers((Boolean)member.getValue());
          }
          break;
        case "roundRobinInetAddress":
          if (member.getValue() instanceof Boolean) {
            obj.setRoundRobinInetAddress((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(AddressResolverOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(AddressResolverOptions obj, java.util.Map<String, Object> json) {
    if (obj.getHostsPath() != null) {
      json.put("hostsPath", obj.getHostsPath());
    }
    if (obj.getHostsValue() != null) {
      json.put("hostsValue", obj.getHostsValue().toJson());
    }
    json.put("hostsRefreshPeriod", obj.getHostsRefreshPeriod());
    if (obj.getServers() != null) {
      JsonArray array = new JsonArray();
      obj.getServers().forEach(item -> array.add(item));
      json.put("servers", array);
    }
    json.put("optResourceEnabled", obj.isOptResourceEnabled());
    json.put("cacheMinTimeToLive", obj.getCacheMinTimeToLive());
    json.put("cacheMaxTimeToLive", obj.getCacheMaxTimeToLive());
    json.put("cacheNegativeTimeToLive", obj.getCacheNegativeTimeToLive());
    json.put("queryTimeout", obj.getQueryTimeout());
    json.put("maxQueries", obj.getMaxQueries());
    json.put("rdFlag", obj.getRdFlag());
    if (obj.getSearchDomains() != null) {
      JsonArray array = new JsonArray();
      obj.getSearchDomains().forEach(item -> array.add(item));
      json.put("searchDomains", array);
    }
    json.put("ndots", obj.getNdots());
    json.put("rotateServers", obj.isRotateServers());
    json.put("roundRobinInetAddress", obj.isRoundRobinInetAddress());
  }
}
