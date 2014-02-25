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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class JsonElement implements Serializable {

  public boolean isArray() {
    return this instanceof JsonArray;
  }

  public boolean isObject() {
    return this instanceof JsonObject;
  }

  public JsonArray asArray() {
    return (JsonArray) this;
  }

  public JsonObject asObject() {
    return (JsonObject) this;
  }

  protected static void checkMap(Map<String, Object> map) {
    for (Object value: map.values()) {
      checkValue(value);
    }
  }

  protected static void checkList(List list) {
    for (Object value: list) {
      checkValue(value);
    }
  }

  @SuppressWarnings("unchecked")
  protected static Map<String, Object> convertMap(Map<String, Object> map) {
    Map<String, Object> converted = new LinkedHashMap<>(map.size());
    for (Map.Entry<String, Object> entry : map.entrySet()) {
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

  @SuppressWarnings("unchecked")
  protected static void checkValue(Object value) {
    if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
      // OK
    } else if (value instanceof Map) {
      checkMap((Map)value);
    } else if (value instanceof List) {
      checkList((List)value);
    } else {
      throw new VertxException("Cannot have objects of class " + value.getClass() +" in JSON");
    }
  }
}