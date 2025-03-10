/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.impl.JsonUtil;
import io.vertx.core.shareddata.ClusterSerializable;
import io.vertx.core.shareddata.Shareable;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.vertx.core.json.impl.JsonUtil.*;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * A representation of a <a href="http://json.org/">JSON</a> object in Java.
 *
 * Unlike some other languages Java does not have a native understanding of JSON. To enable JSON to be used easily
 * in Vert.x code we use this class to encapsulate the notion of a JSON object.
 *
 * The implementation adheres to the <a href="http://rfc-editor.org/rfc/rfc7493.txt">RFC-7493</a> to support Temporal
 * data types as well as binary data.
 *
 * Please see the documentation for more information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObject implements Iterable<Map.Entry<String, Object>>, ClusterSerializable, Shareable {

  private Map<String, Object> map;

  /**
   * Create an instance from a string of JSON
   *
   * @param json the string of JSON
   */
  public JsonObject(String json) {
    if (json == null) {
      throw new NullPointerException();
    }
    fromJson(json);
    if (map == null) {
      throw new DecodeException("Invalid JSON object: " + json);
    }
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
   * @param map the map to create the instance from.
   */
  public JsonObject(Map<String, Object> map) {
    if (map == null) {
      throw new NullPointerException();
    }
    this.map = map;
  }

  /**
   * Create an instance from a buffer.
   *
   * @param buf the buffer to create the instance from.
   */
  public JsonObject(Buffer buf) {
    if (buf == null) {
      throw new NullPointerException();
    }
    fromBuffer(buf);
    if (map == null) {
      throw new DecodeException("Invalid JSON object: " + buf);
    }
  }

  /**
   * Create a JsonObject containing zero mappings.
   *
   * @return an empty JsonObject.
   */
  public static JsonObject of() {
    return new JsonObject();
  }

  /**
   * Create a JsonObject containing a single mapping.
   *
   * @param k1 the mapping's key
   * @param v1 the mapping's value
   * @return a JsonObject containing the specified mapping.
   */
  public static JsonObject of(String k1, Object v1) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(1));

    obj.put(k1, v1);

    return obj;
  }

  /**
   * Create a JsonObject containing two mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(2));

    obj.put(k1, v1);
    obj.put(k2, v2);

    return obj;
  }

  /**
   * Create a JsonObject containing three mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(3));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);

    return obj;
  }

  /**
   * Create a JsonObject containing four mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(4));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);

    return obj;
  }

  /**
   * Create a JsonObject containing five mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4, String k5, Object v5) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(5));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);

    return obj;
  }

  /**
   * Create a JsonObject containing six mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4, String k5, Object v5, String k6, Object v6) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(6));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);
    obj.put(k6, v6);

    return obj;
  }

  /**
   * Create a JsonObject containing seven mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4, String k5, Object v5, String k6, Object v6,
                              String k7, Object v7) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(7));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);
    obj.put(k6, v6);
    obj.put(k7, v7);

    return obj;
  }

  /**
   * Create a JsonObject containing eight mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @param k8 the eighth mapping's key
   * @param v8 the eighth mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4, String k5, Object v5, String k6, Object v6,
                              String k7, Object v7, String k8, Object v8) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(8));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);
    obj.put(k6, v6);
    obj.put(k7, v7);
    obj.put(k8, v8);

    return obj;
  }

  /**
   * Create a JsonObject containing nine mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @param k8 the eighth mapping's key
   * @param v8 the eighth mapping's value
   * @param k9 the ninth mapping's key
   * @param v9 the ninth mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4, String k5, Object v5, String k6, Object v6,
                              String k7, Object v7, String k8, Object v8, String k9, Object v9) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(9));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);
    obj.put(k6, v6);
    obj.put(k7, v7);
    obj.put(k8, v8);
    obj.put(k9, v9);

    return obj;
  }

  /**
   * Create a JsonObject containing ten mappings.
   *
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @param k8 the eighth mapping's key
   * @param v8 the eighth mapping's value
   * @param k9 the ninth mapping's key
   * @param v9 the ninth mapping's value
   * @param k10 the tenth mapping's key
   * @param v10 the tenth mapping's value
   * @return a JsonObject containing the specified mappings.
   */
  public static JsonObject of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                              String k4, Object v4, String k5, Object v5, String k6, Object v6,
                              String k7, Object v7, String k8, Object v8, String k9, Object v9,
                              String k10, Object v10) {
    JsonObject obj = new JsonObject(new LinkedHashMap<>(10));

    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);
    obj.put(k6, v6);
    obj.put(k7, v7);
    obj.put(k8, v8);
    obj.put(k9, v9);
    obj.put(k10, v10);

    return obj;
  }

  /**
   * Create a JsonObject from the fields of a Java object.
   * Faster than calling `new JsonObject(Json.encode(obj))`.
   * <p/
   * Returns {@code null} when {@code obj} is {@code null}.
   *
   * @param obj The object to convert to a JsonObject.
   * @throws IllegalArgumentException if conversion fails due to an incompatible type.
   */
  @SuppressWarnings("unchecked")
  public static JsonObject mapFrom(Object obj) {
    if (obj == null) {
      return null;
    } else {
      return new JsonObject((Map<String, Object>) Json.CODEC.fromValue(obj, Map.class));
    }
  }

  /**
   * Instantiate a Java object from a JsonObject.
   * Faster than calling `Json.decodeValue(Json.encode(jsonObject), type)`.
   *
   * @param type The type to instantiate from the JsonObject.
   * @throws IllegalArgumentException if the type cannot be instantiated.
   */
  public <T> T mapTo(Class<T> type) {
    return Json.CODEC.fromValue(map, type);
  }

  /**
   * Get the string value with the specified key, special cases are addressed for extended JSON types {@code Instant},
   * {@code byte[]} and {@code Enum} which can be converted to String.
   *
   * @param key the key to return the value for
   * @return the value string representation or null if no value for that key
   */
  public String getString(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val == null) {
      return null;
    }

    if (val instanceof Instant) {
      return ISO_INSTANT.format((Instant) val);
    } else if (val instanceof byte[]) {
      return BASE64_ENCODER.encodeToString((byte[]) val);
    } else if (val instanceof Buffer) {
      return BASE64_ENCODER.encodeToString(((Buffer) val).getBytes());
    } else if (val instanceof Enum) {
      return ((Enum) val).name();
    } else {
      return val.toString();
    }
  }

  /**
   * Get the Number value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Number
   */
  public Number getNumber(String key) {
    Objects.requireNonNull(key);
    return (Number) map.get(key);
  }

  /**
   * Get the Integer value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not an Integer
   */
  public Integer getInteger(String key) {
    Objects.requireNonNull(key);
    Number number = (Number) map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Integer) {
      return (Integer) number;  // Avoids unnecessary unbox/box
    } else {
      return number.intValue();
    }
  }

  /**
   * Get the Long value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Long
   */
  public Long getLong(String key) {
    Objects.requireNonNull(key);
    Number number = (Number) map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Long) {
      return (Long) number;  // Avoids unnecessary unbox/box
    } else {
      return number.longValue();
    }
  }

  /**
   * Get the Double value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Double
   */
  public Double getDouble(String key) {
    Objects.requireNonNull(key);
    Number number = (Number) map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Double) {
      return (Double) number;  // Avoids unnecessary unbox/box
    } else {
      return number.doubleValue();
    }
  }

  /**
   * Get the Float value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Float
   */
  public Float getFloat(String key) {
    Objects.requireNonNull(key);
    Number number = (Number) map.get(key);
    if (number == null) {
      return null;
    } else if (number instanceof Float) {
      return (Float) number;  // Avoids unnecessary unbox/box
    } else {
      return number.floatValue();
    }
  }

  /**
   * Get the Boolean value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a Boolean
   */
  public Boolean getBoolean(String key) {
    Objects.requireNonNull(key);
    return (Boolean) map.get(key);
  }

  /**
   * Get the JsonObject value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a JsonObject
   */
  public JsonObject getJsonObject(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof Map) {
      val = new JsonObject((Map) val);
    }
    return (JsonObject) val;
  }

  /**
   * Get the JsonArray value with the specified key
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException if the value is not a JsonArray
   */
  public JsonArray getJsonArray(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    if (val instanceof List) {
      val = new JsonArray((List) val);
    }
    return (JsonArray) val;
  }

  /**
   * Get the binary value with the specified key.
   * <p>
   * JSON itself has no notion of a binary, this extension complies to the RFC-7493, so this method assumes there is a
   * String value with the key and it contains a Base64 encoded binary, which it decodes if found and returns.
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException       if the value is not a String
   * @throws java.lang.IllegalArgumentException if the String value is not a legal Base64 encoded value
   */
  public byte[] getBinary(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    // no-op
    if (val == null) {
      return null;
    }
    // no-op if value is already an byte[]
    if (val instanceof byte[]) {
      return (byte[]) val;
    }
    // unwrap if value is already a Buffer
    if (val instanceof Buffer) {
      return ((Buffer) val).getBytes();
    }
    // assume that the value is in String format as per RFC
    String encoded = (String) val;
    // parse to proper type
    return BASE64_DECODER.decode(encoded);
  }

  /**
   * Get the {@code Buffer} value with the specified key.
   * <p>
   * JSON itself has no notion of a binary, this extension complies to the RFC-7493, so this method assumes there is a
   * String value with the key and it contains a Base64 encoded binary, which it decodes if found and returns.
   *
   * @param key the string to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException       if the value is not a string
   * @throws java.lang.IllegalArgumentException if the value is not a legal Base64 encoded string
   */
  public Buffer getBuffer(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    // no-op
    if (val == null) {
      return null;
    }
    // no-op if value is already an Buffer
    if (val instanceof Buffer) {
      return (Buffer) val;
    }

    // wrap if value is already an byte[]
    if (val instanceof byte[]) {
      return Buffer.buffer((byte[]) val);
    }

    // assume that the value is in String format as per RFC
    String encoded = (String) val;
    // parse to proper type
    return Buffer.buffer(BASE64_DECODER.decode(encoded));
  }

  /**
   * Get the instant value with the specified key.
   * <p>
   * JSON itself has no notion of a temporal types, this extension allows ISO 8601 string formatted dates with timezone
   * always set to zero UTC offset, as denoted by the suffix "Z" to be parsed as a instant value.
   * {@code YYYY-MM-DDTHH:mm:ss.sssZ} is the default format used by web browser scripting. This extension complies to
   * the RFC-7493 with all the restrictions mentioned before. The method will then decode and return a instant value.
   *
   * @param key the key to return the value for
   * @return the value or null if no value for that key
   * @throws java.lang.ClassCastException            if the value is not a String
   * @throws java.time.format.DateTimeParseException if the String value is not a legal ISO 8601 encoded value
   */
  public Instant getInstant(String key) {
    Objects.requireNonNull(key);
    Object val = map.get(key);
    // no-op
    if (val == null) {
      return null;
    }
    // no-op if value is already an Instant
    if (val instanceof Instant) {
      return (Instant) val;
    }
    // assume that the value is in String format as per RFC
    String encoded = (String) val;
    // parse to proper type
    return Instant.from(ISO_INSTANT.parse(encoded));
  }

  /**
   * Get the value with the specified key, as an Object with types respecting the limitations of JSON.
   * <ul>
   *   <li>{@code Map} will be wrapped to {@code JsonObject}</li>
   *   <li>{@code List} will be wrapped to {@code JsonArray}</li>
   *   <li>{@code Instant} will be converted to {@code String}</li>
   *   <li>{@code byte[]} will be converted to {@code String}</li>
   *   <li>{@code Enum} will be converted to {@code String}</li>
   * </ul>
   *
   * @param key the key to lookup
   * @return the value
   */
  public Object getValue(String key) {
    Objects.requireNonNull(key);
    return wrapJsonValue(map.get(key));
  }

  /**
   * Like {@link #getString(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public String getString(String key, String def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getString(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getNumber(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Number getNumber(String key, Number def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getNumber(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getInteger(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Integer getInteger(String key, Integer def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getInteger(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getLong(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Long getLong(String key, Long def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getLong(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getDouble(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Double getDouble(String key, Double def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getDouble(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getFloat(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Float getFloat(String key, Float def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getFloat(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getBoolean(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Boolean getBoolean(String key, Boolean def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getBoolean(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getJsonObject(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public JsonObject getJsonObject(String key, JsonObject def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getJsonObject(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getJsonArray(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public JsonArray getJsonArray(String key, JsonArray def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getJsonArray(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getBinary(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public byte[] getBinary(String key, byte[] def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getBinary(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getBuffer(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Buffer getBuffer(String key, Buffer def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getBuffer(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getInstant(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Instant getInstant(String key, Instant def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getInstant(key);
    } else {
      return def;
    }
  }

  /**
   * Like {@link #getValue(String)} but specifying a default value to return if there is no entry.
   *
   * @param key the key to lookup
   * @param def the default value to use if the entry is not present
   * @return the value or {@code def} if no entry present
   */
  public Object getValue(String key, Object def) {
    Objects.requireNonNull(key);
    if (map.containsKey(key)) {
      return getValue(key);
    } else {
      return def;
    }
  }

  /**
   * Does the JSON object contain the specified key?
   *
   * @param key the key
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
   * Put a null value into the JSON object with the specified key.
   *
   * @param key the key
   * @return a reference to this, so the API can be used fluently
   */
  public JsonObject putNull(String key) {
    Objects.requireNonNull(key);
    map.put(key, null);
    return this;
  }

  /**
   * Put an Object into the JSON object with the specified key.
   *
   * @param key   the key
   * @param value the value
   * @return a reference to this, so the API can be used fluently
   */
  public JsonObject put(String key, Object value) {
    Objects.requireNonNull(key);
    map.put(key, value);
    return this;
  }

  /**
   * Remove an entry from this object.
   *
   * @param key the key
   * @return the value that was removed, or null if none
   */
  public Object remove(String key) {
    Objects.requireNonNull(key);
    return wrapJsonValue(map.remove(key));
  }

  /**
   * Merge in another JSON object.
   * <p>
   * This is the equivalent of putting all the entries of the other JSON object into this object. This is not a deep
   * merge, entries containing (sub) JSON objects will be replaced entirely.
   *
   * @param other the other JSON object
   * @return a reference to this, so the API can be used fluently
   */
  public JsonObject mergeIn(JsonObject other) {
    return mergeIn(other, false);
  }

  /**
   * Merge in another JSON object.
   * A deep merge (recursive) matches (sub) JSON objects in the existing tree and replaces all
   * matching entries. JsonArrays are treated like any other entry, i.e. replaced entirely.
   *
   * @param other the other JSON object
   * @param deep  if true, a deep merge is performed
   * @return a reference to this, so the API can be used fluently
   */
  public JsonObject mergeIn(JsonObject other, boolean deep) {
    return mergeIn(other, deep ? Integer.MAX_VALUE : 1);
  }

  /**
   * Merge in another JSON object.
   * The merge is deep (recursive) to the specified level. If depth is 0, no merge is performed,
   * if depth is greater than the depth of one of the objects, a full deep merge is performed.
   *
   * @param other the other JSON object
   * @param depth depth of merge
   * @return a reference to this, so the API can be used fluently
   */
  @SuppressWarnings("unchecked")
  public JsonObject mergeIn(JsonObject other, int depth) {
    if (depth < 1) {
      return this;
    }
    if (depth == 1) {
      map.putAll(other.map);
      return this;
    }
    for (Map.Entry<String, Object> e : other.map.entrySet()) {
      if (e.getValue() == null) {
        map.put(e.getKey(), null);
      } else {
        map.merge(e.getKey(), e.getValue(), (oldVal, newVal) -> {
          if (oldVal instanceof Map) {
            oldVal = new JsonObject((Map) oldVal);
          }
          if (newVal instanceof Map) {
            newVal = new JsonObject((Map) newVal);
          }
          if (oldVal instanceof JsonObject && newVal instanceof JsonObject) {
            return ((JsonObject) oldVal).mergeIn((JsonObject) newVal, depth - 1);
          }
          return newVal;
        });
      }
    }
    return this;
  }

  /**
   * Encode this JSON object as a string.
   *
   * @return the string encoding.
   */
  public String encode() {
    return Json.CODEC.toString(this, false);
  }

  /**
   * Encode this JSON object a a string, with whitespace to make the object easier to read by a human, or other
   * sentient organism.
   *
   * @return the pretty string encoding.
   */
  public String encodePrettily() {
    return Json.CODEC.toString(this, true);
  }

  /**
   * Encode this JSON object as buffer.
   *
   * @return the buffer encoding.
   */
  public Buffer toBuffer() {
    return Json.CODEC.toBuffer(this, false);
  }

  /**
   * Deep copy of this JSON object.
   *
   * @return a copy where all elements have been copied recursively
   * @throws IllegalStateException when a nested element cannot be copied
   */
  @Override
  public JsonObject copy() {
    return copy(DEFAULT_CLONER);
  }

  /**
   * Deep copy of this JSON object.
   *
   * <p> Unlike {@link #copy()} that can fail when an unknown element cannot be copied, this method
   * delegates the copy of such element to the {@code cloner} function and will not fail.
   *
   * @param cloner a function that copies custom values not supported by the JSON implementation
   * @return a copy where all elements have been copied recursively
   */
  public JsonObject copy(Function<Object, ?> cloner) {
    Map<String, Object> copiedMap;
    if (map instanceof LinkedHashMap) {
      copiedMap = new LinkedHashMap<>(map.size());
    } else {
      copiedMap = new HashMap<>(map.size());
    }
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object val = deepCopy(entry.getValue(), cloner);
      copiedMap.put(entry.getKey(), val);
    }
    return new JsonObject(copiedMap);
  }

  /**
   * Get the underlying {@code Map} as is.
   * <p>
   * This map may contain values that are not the types returned by the {@code JsonObject} and
   * with an unpredictable representation of the value, e.g you might get a JSON object
   * as a {@link JsonObject} or as a {@link Map}.
   *
   * @return the underlying Map.
   */
  public Map<String, Object> getMap() {
    return map;
  }

  /**
   * Get a Stream over the entries in the JSON object. The values in the stream will follow
   * the same rules as defined in {@link #getValue(String)}, respecting the JSON requirements.
   * <p>
   * To stream the raw values, use the storage object stream instead:
   * <pre>{@code
   *   jsonObject
   *     .getMap()
   *     .stream()
   * }</pre>
   *
   * @return a Stream
   */
  public Stream<Map.Entry<String, Object>> stream() {
    return asStream(iterator());
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
   *
   * @return the number of entries
   */
  public int size() {
    return map.size();
  }

  /**
   * Remove all the entries in this JSON object
   */
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
    // null check
    if (o == null) {
      return false;
    }
    // self check
    if (this == o) {
      return true;
    }
    // type check and cast
    if (getClass() != o.getClass()) {
      return false;
    }

    JsonObject other = (JsonObject) o;
    // size check
    if (this.size() != other.size()) {
      return false;
    }
    // value comparison
    for (String key : map.keySet()) {
      if (!other.containsKey(key)) {
        return false;
      }

      Object thisValue = this.getValue(key);
      Object otherValue = other.getValue(key);
      if (thisValue != otherValue && !compare(thisValue, otherValue)) {
        return false;
      }
    }
    // all checks passed
    return true;
  }

  @Override
  public int hashCode() {
    int h = 0;
    for (Map.Entry<String, ?> entry : this) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      h += (key.hashCode() ^ JsonUtil.hashCode(value));
    }
    return h;
  }

  @Override
  public void writeToBuffer(Buffer buffer) {
    Buffer buf = toBuffer();
    buffer.appendInt(buf.length());
    buffer.appendBuffer(buf);
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    int start = pos + 4;
    Buffer buf = buffer.getBuffer(start, start + length);
    fromBuffer(buf);
    return pos + length + 4;
  }

  private void fromJson(String json) {
    map = Json.CODEC.fromString(json, Map.class);
  }

  private void fromBuffer(Buffer buf) {
    map = Json.CODEC.fromBuffer(buf, Map.class);
  }

  private static class Iter implements Iterator<Map.Entry<String, Object>> {

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
      final Map.Entry<String, Object> entry = mapIter.next();
      final Object val = entry.getValue();
      // perform wrapping
      final Object wrapped = wrapJsonValue(val);

      if (val != wrapped) {
        return new Entry(entry.getKey(), wrapped);
      }

      return entry;
    }

    @Override
    public void remove() {
      mapIter.remove();
    }
  }

  private static final class Entry implements Map.Entry<String, Object> {
    final String key;
    final Object value;

    public Entry(String key, Object value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public Object getValue() {
      return value;
    }

    @Override
    public Object setValue(Object value) {
      throw new UnsupportedOperationException();
    }
  }
}
