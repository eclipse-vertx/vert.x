/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.impl.Json;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObject implements Iterable<Map.Entry<String, Object>>, ClusterSerializable {

  private Map<String, Object> map;

  public JsonObject(String json) {
    fromJson(json);
  }

  public JsonObject() {
    map = new LinkedHashMap<>();
  }

  public JsonObject(Map<String, Object> map) {
    this.map = map;
  }

  public String getString(String key) {
    Objects.requireNonNull(key);
    CharSequence cs = (CharSequence)map.get(key);
    return cs == null ? null : cs.toString();
  }

  public Integer getInteger(String key) {
    Objects.requireNonNull(key);
    Number number = (Number)map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Integer) {
      return (Integer)number;  // Avoids unnecessary unbox/box
    } else {
      return number.intValue();
    }
  }

  public Long getLong(String key) {
    Objects.requireNonNull(key);
    Number number = (Number)map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Long) {
      return (Long)number;  // Avoids unnecessary unbox/box
    } else {
      return number.longValue();
    }
  }

  public Double getDouble(String key) {
    Objects.requireNonNull(key);
    Number number = (Number)map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Double) {
      return (Double)number;  // Avoids unnecessary unbox/box
    } else {
      return number.doubleValue();
    }
  }

  public Float getFloat(String key) {
    Objects.requireNonNull(key);
    Number number = (Number)map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Float) {
      return (Float)number;  // Avoids unnecessary unbox/box
    } else {
      return number.floatValue();
    }
  }

  public Boolean getBoolean(String key) {
    Objects.requireNonNull(key);
    return (Boolean)map.get(key);
  }

  public JsonObject getJsonObject(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof Map) {
      val = new JsonObject((Map)val);
    }
    return (JsonObject)val;
  }

  public JsonArray getJsonArray(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof List) {
      val = new JsonArray((List)val);
    }
    return (JsonArray)val;
  }

  public byte[] getBinary(String key) {
    Objects.requireNonNull(key);
    String encoded = (String) map.get(key);
    return encoded == null ? null : Base64.getDecoder().decode(encoded);
  }

  public Object getValue(String key) {
    Objects.requireNonNull(key);
    return map.get(key);
  }

  public String getString(String key, String def) {
    Objects.requireNonNull(key);
    CharSequence cs = (CharSequence)map.get(key);
    return cs != null || map.containsKey(key) ? cs == null ? null : cs.toString() : def;
  }

  public Integer getInteger(String key, Integer def) {
    Objects.requireNonNull(key);
    Number val = (Number)map.get(key);
    if (val == null) {
      if (map.containsKey(key)) {
        return null;
      } else {
        return def;
      }
    } else if (val instanceof Integer) {
      return (Integer)val;  // Avoids unnecessary unbox/box
    } else {
      return val.intValue();
    }
  }

  public Long getLong(String key, Long def) {
    Objects.requireNonNull(key);
    Number val = (Number)map.get(key);
    if (val == null) {
      if (map.containsKey(key)) {
        return null;
      } else {
        return def;
      }
    } else if (val instanceof Long) {
      return (Long)val;  // Avoids unnecessary unbox/box
    } else {
      return val.longValue();
    }
  }

  public Double getDouble(String key, Double def) {
    Objects.requireNonNull(key);
    Number val = (Number)map.get(key);
    if (val == null) {
      if (map.containsKey(key)) {
        return null;
      } else {
        return def;
      }
    } else if (val instanceof Double) {
      return (Double)val;  // Avoids unnecessary unbox/box
    } else {
      return val.doubleValue();
    }
  }

  public Float getFloat(String key, Float def) {
    Objects.requireNonNull(key);
    Number val = (Number)map.get(key);
    if (val == null) {
      if (map.containsKey(key)) {
        return null;
      } else {
        return def;
      }
    } else if (val instanceof Float) {
      return (Float)val;  // Avoids unnecessary unbox/box
    } else {
      return val.floatValue();
    }
  }

  public Boolean getBoolean(String key, Boolean def) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    return val != null || map.containsKey(key) ? (Boolean)val : def;
  }

  public JsonObject getJsonObject(String key, JsonObject def) {
    JsonObject val = getJsonObject(key);
    return val != null || map.containsKey(key) ? val : def;
  }

  public JsonArray getJsonArray(String key, JsonArray def) {
    JsonArray val = getJsonArray(key);
    return val != null || map.containsKey(key) ? val : def;
  }

  public byte[] getBinary(String key, byte[] def) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    return val != null || map.containsKey(key) ? (val == null ? null : Base64.getDecoder().decode((String)val)) : def;
  }

  public Object getValue(String key, Object def) {
    Objects.requireNonNull(key);
    Object val = getValue(key);
    return val != null || map.containsKey(key) ? val : def;
  }

  public boolean containsKey(String key) {
    Objects.requireNonNull(key);
    return map.containsKey(key);
  }

  public Set<String> fieldNames() {
    return map.keySet();
  }

  public JsonObject put(String key, CharSequence value) {
    Objects.requireNonNull(key);
    map.put(key, value == null ? null : value.toString());
    return this;
  }

  public JsonObject put(String key, String value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, Integer value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, Long value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, Double value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, Float value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, Boolean value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject putNull(String key) {
    Objects.requireNonNull(key);
    map.put(key, null);
    return this;
  }

  public JsonObject put(String key, JsonObject value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, JsonArray value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  public JsonObject put(String key, byte[] value) {
    Objects.requireNonNull(key);
    map.put(key, value == null ? null : Base64.getEncoder().encodeToString(value));
    return this;
  }

  public JsonObject put(String key, Object value) {
    Objects.requireNonNull(key);
    value = Json.checkAndCopy(value, false);
    map.put(key, value);
    return this;
  }

  public Object remove(String key) {
    return map.remove(key);
  }

  public JsonObject mergeIn(JsonObject other) {
    map.putAll(other.map);
    return this;
  }

  public String encode() {
    return Json.encode(map);
  }

  public String encodePrettily() {
    return Json.encodePrettily(map);
  }

  public JsonObject copy() {
    Map<String, Object> copiedMap = new HashMap<>(map.size());
    for (Map.Entry<String, Object> entry: map.entrySet()) {
      Object val = entry.getValue();
      val = Json.checkAndCopy(val, true);
      copiedMap.put(entry.getKey(), val);
    }
    return new JsonObject(copiedMap);
  }

  public Map<String, Object> getMap() {
    return map;
  }

  public Stream<Map.Entry<String, Object>> stream() {
    return map.entrySet().stream();
  }

  @Override
  public Iterator<Map.Entry<String, Object>> iterator() {
    return new Iter(map.entrySet().iterator());
  }

  public int size() {
    return map.size();
  }

  public JsonObject clear() {
    map.clear();
    return this;
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public String toString() {
    return encode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    return objectEquals(map, o);
  }

  static boolean objectEquals(Map<?, ?> m1, Object o2) {
    Map<?, ?> m2;
    if (o2 instanceof JsonObject) {
      m2 = ((JsonObject) o2).map;
    } else if (o2 instanceof Map<?, ?>) {
      m2 = (Map<?, ?>) o2;
    } else {
      return false;
    }
    if (m1.size() != m2.size())
      return false;
    for (Map.Entry<?, ?> entry : m1.entrySet()) {
      Object val = entry.getValue();
      if (val == null) {
        if (m2.get(entry.getKey()) != null) {
          return false;
        }
      } else {
        if (!equals(entry.getValue(), m2.get(entry.getKey()))) {
          return false;
        }
      }
    }
    return true;
  }

  static boolean equals(Object o1, Object o2) {
    if (o1 == o2)
      return true;
    if (o1 instanceof JsonObject) {
      return objectEquals(((JsonObject) o1).map, o2);
    }
    if (o1 instanceof Map<?, ?>) {
      return objectEquals((Map<?, ?>) o1, o2);
    }
    if (o1 instanceof JsonArray) {
      return JsonArray.arrayEquals(((JsonArray) o1).getList(), o2);
    }
    if (o1 instanceof List<?>) {
      return JsonArray.arrayEquals((List<?>) o1, o2);
    }
    if (o1 instanceof Number && o2 instanceof Number && o1.getClass() != o2.getClass()) {
      Number n1 = (Number) o1;
      Number n2 = (Number) o2;
      if (o1 instanceof Float || o1 instanceof Double || o2 instanceof Float || o2 instanceof Double) {
        return n1.doubleValue() == n2.doubleValue();
      } else {
        return n1.longValue() == n2.longValue();
      }
    }
    return o1.equals(o2);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public Buffer writeToBuffer() {
    String encoded = encode();
    byte[] bytes = encoded.getBytes();
    Buffer buffer = Buffer.buffer(bytes.length + 4);
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
    return buffer;
  }

  @Override
  public void readFromBuffer(Buffer buffer) {
    int length = buffer.getInt(0);
    String encoded = buffer.getString(4, 4 + length);
    fromJson(encoded);
  }

  private void fromJson(String json) {
    map = Json.decodeValue(json, Map.class);
  }

  private class Iter implements Iterator<Map.Entry<String, Object>> {

    final Iterator<Map.Entry<String, Object>> mapIter;

    Iter(Iterator<Map.Entry<String, Object>> mapIter) {
      this.mapIter = mapIter;
    }

    @Override
    public boolean hasNext() {
      return mapIter.hasNext();
    }

    @Override
    public Map.Entry<String, Object> next() {
      Map.Entry<String, Object> entry = mapIter.next();
      if (entry.getValue() instanceof Map) {
        entry.setValue(new JsonObject((Map)entry.getValue()));
      } else if (entry.getValue() instanceof List) {
        entry.setValue(new JsonArray((List) entry.getValue()));
      }
      return entry;
    }

    @Override
    public void remove() {
      mapIter.remove();
    }
  }
}
