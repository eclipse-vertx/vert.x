package org.vertx.java.core.json;

import org.vertx.java.core.http.ws.Base64;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObject {

  final Map<String, Object> map;

  public JsonObject(Map<String, Object> map) {
    this.map = map;
  }

  public JsonObject() {
    this.map = new HashMap<>();
  }

  public JsonObject(String jsonString) {
    try {
      map = Json.mapper.readValue(jsonString, Map.class);
      } catch (Exception e) {
      throw new DecodeException("Failed to decode JSON object from string: " + jsonString);
    }
  }

  public JsonObject putString(String fieldName, String value) {
    map.put(fieldName, value);
    return this;
  }

  public JsonObject putObject(String fieldName, JsonObject value) {
    map.put(fieldName, value.map);
    return this;
  }

  public JsonObject putArray(String fieldName, JsonArray value) {
    map.put(fieldName, value.list);
    return this;
  }

  public JsonObject putNumber(String fieldName, Number value) {
    map.put(fieldName, value);
    return this;
  }

  public JsonObject putBoolean(String fieldName, Boolean value) {
    map.put(fieldName, value);
    return this;
  }

  public JsonObject putBinary(String fieldName, byte[] binary) {
    map.put(fieldName, Base64.encodeBytes(binary));
    return this;
  }

  public String getString(String fieldName) {
    return (String)map.get(fieldName);
  }

  public JsonObject getObject(String fieldName) {
    Map m = (Map)map.get(fieldName);
    return m == null ? null : new JsonObject(m);
  }

  public JsonArray getArray(String fieldName) {
    List l = (List)map.get(fieldName);
    return l == null ? null : new JsonArray(l);
  }

  public Number getNumber(String fieldName) {
    return (Number)map.get(fieldName);
  }

  public Boolean getBoolean(String fieldName) {
    return (Boolean)map.get(fieldName);
  }

  public byte[] getBinary(String fieldName) {
    String encoded = (String)map.get(fieldName);
    return Base64.decode(encoded);
  }

  public Set<String> getFieldNames() {
    return map.keySet();
  }

  public Object getField(String fieldName) {
    Object obj = map.get(fieldName);
    if (obj instanceof Map) {
      return new JsonObject((Map)obj);
    } else if (obj instanceof List) {
      return new JsonArray((List)obj);
    } else {
      return obj;
    }
  }

  public Object removeField(String fieldName) {
    return map.remove(fieldName) != null;
  }

  public int size() {
    return map.size();
  }

  public JsonObject mergeIn(JsonObject other) {
    map.putAll(other.map);
    return this;
  }

  public String encode() throws EncodeException {
    return Json.encode(this.map);
  }

  public JsonObject copy() {
    return new JsonObject(encode());
  }

  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    JsonObject that = (JsonObject) o;

    if (this.map.size() != that.map.size()) return false;

    for (Map.Entry<String, Object> entry: this.map.entrySet()) {
      Object val = entry.getValue();
      if (val == null) {
        if (that.map.get(entry.getKey()) != null) {
          return false;
        }
      } else {
        if (!entry.getValue().equals(that.map.get(entry.getKey()))) {
          return false;
        }
      }
    }
    return true;
  }

}
