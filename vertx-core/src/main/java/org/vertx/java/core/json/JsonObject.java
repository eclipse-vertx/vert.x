/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.json;

import org.vertx.java.core.http.impl.ws.Base64;
import org.vertx.java.core.json.impl.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * Represents a JSON object
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObject {

  final Map<String, Object> map;

  /**
   * Create a JSON object based on the specified Map
   * @param map
   */
  public JsonObject(Map<String, Object> map) {
    this.map = map;
  }

  /**
   * Create an empty JSON object
   */
  public JsonObject() {
    this.map = new HashMap<>();
  }

  /**
   * Create a JSON object from a string form of a JSON object
   * @param jsonString The string form of a JSON object
   */
  @SuppressWarnings("unchecked")
  public JsonObject(String jsonString) {
    map = (Map<String, Object>) Json.decodeValue(jsonString, Map.class);
  }

  public JsonObject putString(String fieldName, String value) {
    map.put(fieldName, value);
    return this;
  }

  public JsonObject putObject(String fieldName, JsonObject value) {
    map.put(fieldName, value == null ? null : value.map);
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

  @SuppressWarnings("unchecked")
  public JsonObject getObject(String fieldName) {
    Map<String, Object> m = (Map<String, Object>) map.get(fieldName);
    return m == null ? null : new JsonObject(m);
  }

  @SuppressWarnings("unchecked")
  public JsonArray getArray(String fieldName) {
    List<Object> l = (List<Object>) map.get(fieldName);
    return l == null ? null : new JsonArray(l);
  }

  public Number getNumber(String fieldName) {
    return (Number)map.get(fieldName);
  }

  public Long getLong(String fieldName) {
    Number num = (Number)map.get(fieldName);
    return num == null ? null : num.longValue();
  }

  public Integer getInteger(String fieldName) {
    Number num = (Number)map.get(fieldName);
    return num == null ? null : num.intValue();
  }

  public Boolean getBoolean(String fieldName) {
    return (Boolean)map.get(fieldName);
  }

  public byte[] getBinary(String fieldName) {
    String encoded = (String)map.get(fieldName);
    return Base64.decode(encoded);
  }

  public String getString(String fieldName, String def) {
    String str = getString(fieldName);
    return str == null ? def : str;
  }

  public JsonObject getObject(String fieldName, JsonObject def) {
    JsonObject obj = getObject(fieldName);
    return obj == null ? def : obj;
  }

  public JsonArray getArray(String fieldName, JsonArray def) {
    JsonArray arr = getArray(fieldName);
    return arr == null ? def : arr;
  }

  public boolean getBoolean(String fieldName, boolean def) {
    Boolean b = getBoolean(fieldName);
    return b == null ? def : b;
  }

  public Number getNumber(String fieldName, int def) {
    Number n = getNumber(fieldName);
    return n == null ? def : n;
  }

  public byte[] getBinary(String fieldName, byte[] def) {
    byte[] b = getBinary(fieldName);
    return b == null ? def : b;
  }

  public Set<String> getFieldNames() {
    return map.keySet();
  }

  @SuppressWarnings("unchecked")
  public Object getField(String fieldName) {
    Object obj = map.get(fieldName);
    if (obj instanceof Map) {
      return new JsonObject((Map<String, Object>) obj);
    } else if (obj instanceof List) {
      return new JsonArray((List<Object>) obj);
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

  public String encode() {
    return Json.encode(this.map);
  }

  public JsonObject copy() {
    return new JsonObject(encode());
  }

  public String toString() {
    return encode();
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

  public Map<String, Object> toMap() {
    return convertMap(map);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> convertMap(Map<String, Object> map) {
    Map<String, Object> converted = new HashMap<>(map.size());
    for (Map.Entry<String, Object> entry: map.entrySet()) {
      Object obj = entry.getValue();
      if (obj instanceof Map) {
        Map<String, Object> jm = (Map<String, Object>) obj;
        converted.put(entry.getKey(), convertMap(jm));
      } else if (obj instanceof List) {
        List<Object> list = (List<Object>) obj;
        converted.put(entry.getKey(), JsonArray.convertList(list));
      } else {
        converted.put(entry.getKey(), obj);
      }
    }
    return converted;
  }
}
