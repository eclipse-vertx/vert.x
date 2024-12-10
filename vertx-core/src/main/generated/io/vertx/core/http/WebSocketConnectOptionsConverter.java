package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.WebSocketConnectOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.WebSocketConnectOptions} original class using Vert.x codegen.
 */
public class WebSocketConnectOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, WebSocketConnectOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "version":
          if (member.getValue() instanceof String) {
            obj.setVersion(io.vertx.core.http.WebSocketVersion.valueOf((String)member.getValue()));
          }
          break;
        case "subProtocols":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setSubProtocols(list);
          }
          break;
        case "allowOriginHeader":
          if (member.getValue() instanceof Boolean) {
            obj.setAllowOriginHeader((Boolean)member.getValue());
          }
          break;
        case "registerWriteHandlers":
          if (member.getValue() instanceof Boolean) {
            obj.setRegisterWriteHandlers((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(WebSocketConnectOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(WebSocketConnectOptions obj, java.util.Map<String, Object> json) {
    if (obj.getVersion() != null) {
      json.put("version", obj.getVersion().name());
    }
    if (obj.getSubProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getSubProtocols().forEach(item -> array.add(item));
      json.put("subProtocols", array);
    }
    json.put("allowOriginHeader", obj.getAllowOriginHeader());
    json.put("registerWriteHandlers", obj.isRegisterWriteHandlers());
  }
}
