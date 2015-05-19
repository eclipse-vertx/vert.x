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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.impl.Json;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

/**
 * A representation of a <a href="http://json.org/">JSON</a> object in Java.
 * <p>
 * Unlike some other languages Java does not have a native understanding of JSON. To enable JSON to be used easily
 * in Vert.x code we use this class to encapsulate the notion of a JSON object.
 * <p>
 * Please see the documentation for more information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObject implements Iterable<Map.Entry<String, Object>>, ClusterSerializable {

  private Map<String, Object> map;

  /**
   * Create an instance from a string of JSON
   *
   * @param json  the string of JSON
   */
  public JsonObject(String json) {
    fromJson(json);
  }

  /**
   * Create a new, empty instance
   */
  public JsonObject() {
    map = new LinkedHashMap<>();
  }

  /**
   * Create an instance from a Map. The Map is not copied.
   *
   * @param map  the map to create the instance from.
   */
  public JsonObject(Map<String, Object> map) {
    this.map = map;
  }

  /**
   * Get the string value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a String
   */
  public String getString(String key) {
    Objects.requireNonNull(key);
    CharSequence cs = (CharSequence)map.get(key);
    return cs == null ? null : cs.toString();
  }

  /**
   * Get the Integer value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not an Integer
   */
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

  /**
   * Get the Long value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Long
   */
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

  /**
   * Get the Double value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Double
   */
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

  /**
   * Get the Float value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Float
   */
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

  /**
   * Get the Boolean value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Boolean
   */
  public Boolean getBoolean(String key) {
    Objects.requireNonNull(key);
    return (Boolean)map.get(key);
  }

  /**
   * Get the JsonObject value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a JsonObject
   */
  public JsonObject getJsonObject(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof Map) {
      val = new JsonObject((Map)val);
    }
    return (JsonObject)val;
  }

  /**
   * Get the JsonArray value with the specified key
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a JsonArray
   */
  public JsonArray getJsonArray(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof List) {
      val = new JsonArray((List)val);
    }
    return (JsonArray)val;
  }

  /**
   * Get the binary value with the specified key.
   * <p>
   * JSON itself has no notion of a binary, so this method assumes there is a String value with the key and
   * it contains a Base64 encoded binary, which it decodes if found and returns.
   * <p>
   * This method should be used in conjunction with {@link #put(String, byte[])}
   *
   * @param key  the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a String
   * @throws java.lang.IllegalArgumentException if the String value is not a legal Base64 encoded value
   */
  public byte[] getBinary(String key) {
    Objects.requireNonNull(key);
    String encoded = (String) map.get(key);
    return encoded == null ? null : Base64.getDecoder().decode(encoded);
  }

  /**
   * Get the value with the specified key, as an Object
   * @param key  the key to lookup
   * @return the value
   */
  public Object getValue(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof Map) {
      val = new JsonObject((Map)val);
    } else if (val instanceof List) {
      val = new JsonArray((List)val);
    }
    return val;
  }

  /**
   * Like {@link #getString(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public String getString(String key, String def) {
    Objects.requireNonNull(key);
    CharSequence cs = (CharSequence)map.get(key);
    return cs != null || map.containsKey(key) ? cs == null ? null : cs.toString() : def;
  }

  /**
   * Like {@link #getInteger(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
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

  /**
   * Like {@link #getLong(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
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

  /**
   * Like {@link #getDouble(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
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

  /**
   * Like {@link #getFloat(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
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

  /**
   * Like {@link #getBoolean(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Boolean getBoolean(String key, Boolean def) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    return val != null || map.containsKey(key) ? (Boolean)val : def;
  }

  /**
   * Like {@link #getJsonObject(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public JsonObject getJsonObject(String key, JsonObject def) {
    JsonObject val = getJsonObject(key);
    return val != null || map.containsKey(key) ? val : def;
  }

  /**
   * Like {@link #getJsonArray(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public JsonArray getJsonArray(String key, JsonArray def) {
    JsonArray val = getJsonArray(key);
    return val != null || map.containsKey(key) ? val : def;
  }

  /**
   * Like {@link #getBinary(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public byte[] getBinary(String key, byte[] def) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    return val != null || map.containsKey(key) ? (val == null ? null : Base64.getDecoder().decode((String)val)) : def;
  }

  /**
   * Like {@link #getValue(String)} but specifying a default value to return if there is no entry.
   *
   * @param key  the key to lookup
   * @param def  the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Object getValue(String key, Object def) {
    Objects.requireNonNull(key);
    Object val = getValue(key);
    return val != null || map.containsKey(key) ? val : def;
  }

  /**
   * Does the JSON object contain the specified key?
   *
   * @param key  the key
   * @return true if it contains the key, false if not.
   */
  public boolean containsKey(String key) {
    Objects.requireNonNull(key);
    return map.containsKey(key);
  }

  /**
   * Return the set of field names in the JSON objects
   *
   * @return the set of field names
   */
  public Set<String> fieldNames() {
    return map.keySet();
  }

  /**
   * Put an Enum into the JSON object with the specified key.
   * <p>
   * JSON has no concept of encoding Enums, so the Enum will be converted to a String using the {@link java.lang.Enum#name}
   * method and the value put as a String.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Enum value) {
    Objects.requireNonNull(key);
    map.put(key, value == null ? null : value.name());
    return this;
  }

  /**
   * Put an CharSequence into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, CharSequence value) {
    Objects.requireNonNull(key);
    map.put(key, value == null ? null : value.toString());
    return this;
  }

  /**
   * Put a String into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, String value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put an Integer into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Integer value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a Long into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Long value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a Double into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Double value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a Float into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Float value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a Boolean into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Boolean value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a null value into the JSON object with the specified key.
   *
   * @param key  the key
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject putNull(String key) {
    Objects.requireNonNull(key);
    map.put(key, null);
    return this;
  }

  /**
   * Put another JSON object into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, JsonObject value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a JSON array into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, JsonArray value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Put a byte[] into the JSON object with the specified key.
   * <p>
   * JSON has no notion of binary, so the binary will first be Base64 encoded before being put as a String.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, byte[] value) {
    Objects.requireNonNull(key);
    map.put(key, value == null ? null : Base64.getEncoder().encodeToString(value));
    return this;
  }

  /**
   * Put an Object into the JSON object with the specified key.
   *
   * @param key  the key
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Object value) {
    Objects.requireNonNull(key);
    value = Json.checkAndCopy(value, false);
    map.put(key, value);
    return this;
  }

  /**
   * Remove an entry from this object.
   *
   * @param key  the key
   * @return the value that was removed, or null if none
   */
  public Object remove(String key) {
    return map.remove(key);
  }

  /**
   * Merge in another JSON object.
   * <p>
   * This is the equivalent of putting all the entries of the other JSON object into this object.
   * @param other  the other JSON object
   * @return a reference to this, so the API can be used fluently
   */
  public JsonObject mergeIn(JsonObject other) {
    map.putAll(other.map);
    return this;
  }

  /**
   * Encode this JSON object as a string.
   *
   * @return the string encoding.
   */
  public String encode() {
    return Json.encode(map);
  }

  /**
   * Encode this JSON object a a string, with whitespace to make the object easier to read by a human, or other
   * sentient organism.
   *
   * @return the pretty string encoding.
   */
  public String encodePrettily() {
    return Json.encodePrettily(map);
  }

  /**
   * Copy the JSON object
   *
   * @return a copy of the object
   */
  public JsonObject copy() {
    Map<String, Object> copiedMap = new HashMap<>(map.size());
    for (Map.Entry<String, Object> entry: map.entrySet()) {
      Object val = entry.getValue();
      val = Json.checkAndCopy(val, true);
      copiedMap.put(entry.getKey(), val);
    }
    return new JsonObject(copiedMap);
  }

  /**
   * Get the underlying Map.
   *
   * @return the underlying Map.
   */
  public Map<String, Object> getMap() {
    return map;
  }

  /**
   * Get a stream of the entries in the JSON object.
   *
   * @return a stream of the entries.
   */
  public Stream<Map.Entry<String, Object>> stream() {
    return map.entrySet().stream();
  }

  /**
   * Get an Iterator of the entries in the JSON object.
   *
   * @return an Iterator of the entries
   */
  @Override
  public Iterator<Map.Entry<String, Object>> iterator() {
    return new Iter(map.entrySet().iterator());
  }

  /**
   * Get the number of entries in the JSON object
   * @return the number of entries
   */
  public int size() {
    return map.size();
  }

  /**
   * Remove all the entries in this JSON object
   */
  @Fluent
  public JsonObject clear() {
    map.clear();
    return this;
  }

  /**
   * Is this object entry?
   *
   * @return true if it has zero entries, false if not.
   */
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
  public void writeToBuffer(Buffer buffer) {
    String encoded = encode();
    byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    int start = pos + 4;
    String encoded = buffer.getString(start, start + length);
    fromJson(encoded);
    return pos + length + 4;
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
