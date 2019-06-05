package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.http.RequestOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.RequestOptions} original class using Vert.x codegen.
 */
public class RequestOptionsConverter implements JsonCodec<RequestOptions, JsonObject> {

  public static final RequestOptionsConverter INSTANCE = new RequestOptionsConverter();

  @Override public JsonObject encode(RequestOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public RequestOptions decode(JsonObject value) { return (value != null) ? new RequestOptions(value) : null; }

  @Override public Class<RequestOptions> getTargetClass() { return RequestOptions.class; }

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RequestOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "headers":
          if (member.getValue() instanceof JsonObject) {
            ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
              if (entry.getValue() instanceof String)
                obj.addHeader(entry.getKey(), (String)entry.getValue());
            });
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "ssl":
          if (member.getValue() instanceof Boolean) {
            obj.setSsl((Boolean)member.getValue());
          }
          break;
        case "uri":
          if (member.getValue() instanceof String) {
            obj.setURI((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(RequestOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RequestOptions obj, java.util.Map<String, Object> json) {
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("port", obj.getPort());
    if (obj.isSsl() != null) {
      json.put("ssl", obj.isSsl());
    }
    if (obj.getURI() != null) {
      json.put("uri", obj.getURI());
    }
  }
}
