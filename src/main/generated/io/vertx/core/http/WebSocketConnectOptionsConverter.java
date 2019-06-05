package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.http.WebSocketConnectOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.WebSocketConnectOptions} original class using Vert.x codegen.
 */
public class WebSocketConnectOptionsConverter implements JsonCodec<WebSocketConnectOptions, JsonObject> {

  public static final WebSocketConnectOptionsConverter INSTANCE = new WebSocketConnectOptionsConverter();

  @Override public JsonObject encode(WebSocketConnectOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public WebSocketConnectOptions decode(JsonObject value) { return (value != null) ? new WebSocketConnectOptions(value) : null; }

  @Override public Class<WebSocketConnectOptions> getTargetClass() { return WebSocketConnectOptions.class; }

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, WebSocketConnectOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
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
        case "version":
          if (member.getValue() instanceof String) {
            obj.setVersion(io.vertx.core.http.WebsocketVersion.valueOf((String)member.getValue()));
          }
          break;
      }
    }
  }

  public static void toJson(WebSocketConnectOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(WebSocketConnectOptions obj, java.util.Map<String, Object> json) {
    if (obj.getSubProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getSubProtocols().forEach(item -> array.add(item));
      json.put("subProtocols", array);
    }
    if (obj.getVersion() != null) {
      json.put("version", obj.getVersion().name());
    }
  }
}
