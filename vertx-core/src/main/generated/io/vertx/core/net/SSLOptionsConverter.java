package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.net.SSLOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.SSLOptions} original class using Vert.x codegen.
 */
public class SSLOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, SSLOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "enabledCipherSuites":
          if (member.getValue() instanceof JsonArray) {
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                obj.addEnabledCipherSuite((String)item);
            });
          }
          break;
        case "crlPaths":
          if (member.getValue() instanceof JsonArray) {
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                obj.addCrlPath((String)item);
            });
          }
          break;
        case "crlValues":
          if (member.getValue() instanceof JsonArray) {
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                obj.addCrlValue(io.vertx.core.buffer.Buffer.fromJson((String)item));
            });
          }
          break;
        case "useAlpn":
          if (member.getValue() instanceof Boolean) {
            obj.setUseAlpn((Boolean)member.getValue());
          }
          break;
        case "http3":
          if (member.getValue() instanceof Boolean) {
            obj.setHttp3((Boolean)member.getValue());
          }
          break;
        case "initialMaxStreamsBidirectional":
          if (member.getValue() instanceof Number) {
            obj.setInitialMaxStreamsBidirectional(((Number)member.getValue()).longValue());
          }
          break;
        case "enabledSecureTransportProtocols":
          if (member.getValue() instanceof JsonArray) {
            java.util.LinkedHashSet<java.lang.String> list =  new java.util.LinkedHashSet<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setEnabledSecureTransportProtocols(list);
          }
          break;
        case "sslHandshakeTimeout":
          if (member.getValue() instanceof Number) {
            obj.setSslHandshakeTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "sslHandshakeTimeoutUnit":
          if (member.getValue() instanceof String) {
            obj.setSslHandshakeTimeoutUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "applicationLayerProtocols":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setApplicationLayerProtocols(list);
          }
          break;
      }
    }
  }

   static void toJson(SSLOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(SSLOptions obj, java.util.Map<String, Object> json) {
    if (obj.getEnabledCipherSuites() != null) {
      JsonArray array = new JsonArray();
      obj.getEnabledCipherSuites().forEach(item -> array.add(item));
      json.put("enabledCipherSuites", array);
    }
    if (obj.getCrlPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getCrlPaths().forEach(item -> array.add(item));
      json.put("crlPaths", array);
    }
    if (obj.getCrlValues() != null) {
      JsonArray array = new JsonArray();
      obj.getCrlValues().forEach(item -> array.add(item.toJson()));
      json.put("crlValues", array);
    }
    json.put("useAlpn", obj.isUseAlpn());
    json.put("http3", obj.isHttp3());
    json.put("initialMaxStreamsBidirectional", obj.getInitialMaxStreamsBidirectional());
    if (obj.getEnabledSecureTransportProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getEnabledSecureTransportProtocols().forEach(item -> array.add(item));
      json.put("enabledSecureTransportProtocols", array);
    }
    json.put("sslHandshakeTimeout", obj.getSslHandshakeTimeout());
    if (obj.getSslHandshakeTimeoutUnit() != null) {
      json.put("sslHandshakeTimeoutUnit", obj.getSslHandshakeTimeoutUnit().name());
    }
    if (obj.getApplicationLayerProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getApplicationLayerProtocols().forEach(item -> array.add(item));
      json.put("applicationLayerProtocols", array);
    }
  }
}
