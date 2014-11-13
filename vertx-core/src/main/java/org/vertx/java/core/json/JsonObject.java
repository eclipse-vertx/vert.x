/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.json;


import org.vertx.java.core.VertxException;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.json.impl.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 
 * Represents a JSON object.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObject extends JsonElement {

  protected Map<String, Object> map;

  /**
   * Create a JSON object based on the specified Map
   * @param map
   */
  public JsonObject(Map<String, Object> map) {
    this(map, true);
  }

  /**
   * Create an empty JSON object
   */
  public JsonObject() {
    this.map = new LinkedHashMap<>();
  }

  protected JsonObject(Map<String, Object> map, boolean copy) {
    this.map = copy ? convertMap(map) : map;
  }


  /**
   * Create a JSON object from a string form of a JSON object
   * 
   * @param jsonString
   *          The string form of a JSON object
   */
  public JsonObject(String jsonString) {
    map = Json.decodeValue(jsonString, Map.class);
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
    map.put(fieldName, value == null ? null : value.list);
    return this;
  }

  public JsonObject putElement(String fieldName, JsonElement value) {
    if (value == null) {
      map.put(fieldName, null);
      return this;
    } else if (value.isArray()) {
      return putArray(fieldName, value.asArray());
    } else {
      return putObject(fieldName, value.asObject());
    }
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
    map.put(fieldName, binary == null ? null : Base64.encodeBytes(binary));
    return this;
  }

  public JsonObject putValue(String fieldName, Object value) {
    if (value == null) {
      putObject(fieldName, null);
    } else if (value instanceof JsonObject) {
      putObject(fieldName, (JsonObject)value);
    } else if (value instanceof JsonArray) {
      putArray(fieldName, (JsonArray)value);
    } else if (value instanceof String) {
      putString(fieldName, (String)value);
    } else if (value instanceof Number) {
      putNumber(fieldName, (Number)value);
    } else if (value instanceof Boolean) {
      putBoolean(fieldName, (Boolean)value);
    } else if (value instanceof byte[]) {
      putBinary(fieldName, (byte[])value);
    } else {
      throw new VertxException("Cannot put objects of class " + value.getClass() +" in JsonObject");
    }
    return this;
  }

  public String getString(String fieldName) {
    return (String) map.get(fieldName);
  }

  @SuppressWarnings("unchecked")
  public JsonObject getObject(String fieldName) {
    Map<String, Object> m = (Map<String, Object>) map.get(fieldName);
    return m == null ? null : new JsonObject(m, false);
  }

  @SuppressWarnings("unchecked")
  public JsonArray getArray(String fieldName) {
    List<Object> l = (List<Object>) map.get(fieldName);
    return l == null ? null : new JsonArray(l, false);
  }

  public JsonElement getElement(String fieldName) {
    Object element = map.get(fieldName);
    if (element == null) return null;

    if (element instanceof Map<?,?>){
      return getObject(fieldName);
    }
    if (element instanceof List<?>){
      return getArray(fieldName);
    }
    throw new ClassCastException();
  }

  public Number getNumber(String fieldName) {
    return (Number) map.get(fieldName);
  }

  public Long getLong(String fieldName) {
    Number num = (Number) map.get(fieldName);
    return num == null ? null : num.longValue();
  }

  public Integer getInteger(String fieldName) {
    Number num = (Number) map.get(fieldName);
    return num == null ? null : num.intValue();
  }

  public Boolean getBoolean(String fieldName) {
    return (Boolean) map.get(fieldName);
  }

  public byte[] getBinary(String fieldName) {
    String encoded = (String) map.get(fieldName);
    return encoded == null ? null : Base64.decode(encoded);
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

  public JsonElement getElement(String fieldName, JsonElement def) {
    JsonElement elem = getElement(fieldName);
    return elem == null ? def : elem;
  }

  public boolean getBoolean(String fieldName, boolean def) {
    Boolean b = getBoolean(fieldName);
    return b == null ? def : b;
  }

  public Number getNumber(String fieldName, int def) {
    Number n = getNumber(fieldName);
    return n == null ? def : n;
  }

  public Long getLong(String fieldName, long def) {
    Number num = (Number) map.get(fieldName);
    return num == null ? def : num.longValue();
  }

  public Integer getInteger(String fieldName, int def) {
    Number num = (Number) map.get(fieldName);
    return num == null ? def : num.intValue();
  }

  public byte[] getBinary(String fieldName, byte[] def) {
    byte[] b = getBinary(fieldName);
    return b == null ? def : b;
  }

  public Set<String> getFieldNames() {
    return map.keySet();
  }

  @SuppressWarnings("unchecked")
  public <T> T getValue(String fieldName) {
    return getField(fieldName);
  }

  @SuppressWarnings("unchecked")
  public <T> T getField(String fieldName) {
    Object obj = map.get(fieldName);
    if (obj instanceof Map) {
      obj = new JsonObject((Map)obj, false);
    } else if (obj instanceof List) {
      obj = new JsonArray((List)obj, false);
    }
    return (T)obj;
  }

  public Object removeField(String fieldName) {
    return map.remove(fieldName);
  }

  /**
    * The containsField() method returns a boolean indicating whether the object has the specified property.
    * @param fieldName to lookup
    * @return true if property exist (null value is also considered to exist).
    */
  public boolean containsField(String fieldName) {
    return map.containsKey(fieldName);
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

  public String encodePrettily() {
    return Json.encodePrettily(this.map);
  }

  /**
   * @return a copy of this JsonObject such that changes in the original are not reflected in the copy, and
   * vice versa
   */
  public JsonObject copy() {
    return new JsonObject(map, true);
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

    JsonObject that = (JsonObject) o;

    if (this.map.size() != that.map.size())
      return false;

    for (Map.Entry<String, Object> entry : this.map.entrySet()) {
      Object val = entry.getValue();
      if (val == null) {
        if (that.map.get(entry.getKey()) != null) {
          return false;
        }
      } else {
        if (!equals(entry.getValue(), that.map.get(entry.getKey()))) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   *
   * @return the underlying Map for this JsonObject
   */
  public Map<String, Object> toMap() {
    return convertMap(map);
  }

}
