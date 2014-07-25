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

package io.vertx.core.json;

import io.vertx.core.VertxException;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
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

  @SuppressWarnings("unchecked")
  protected Map<String, Object> convertMap(Map<String, Object> map) {
    Map<String, Object> converted = new LinkedHashMap<>(map.size());
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object obj = entry.getValue();
      if (obj instanceof Map) {
        Map<String, Object> jm = (Map<String, Object>) obj;
        converted.put(entry.getKey(), convertMap(jm));
      } else if (obj instanceof List) {
        List<Object> list = (List<Object>) obj;
        converted.put(entry.getKey(), convertList(list));
      } else if (obj instanceof CharSequence) {
        converted.put(entry.getKey(), obj.toString());
      } else if (obj instanceof BigDecimal) {
        converted.put(entry.getKey(), ((BigDecimal) obj).doubleValue());
      } else if (obj instanceof BigInteger) {
        converted.put(entry.getKey(), ((BigInteger )obj).longValue());
      } else if (obj == null || obj instanceof Number || obj instanceof Boolean) {
        // OK
        converted.put(entry.getKey(), obj);
      } else {
        throw new VertxException("Cannot have objects of class " + obj.getClass() +" in JSON");
      }
    }
    return converted;
  }

  @SuppressWarnings("unchecked")
  protected List<Object> convertList(List<?> list) {
    List<Object> arr = new ArrayList<>(list.size());
    for (Object obj : list) {
      if (obj instanceof Map) {
        arr.add(convertMap((Map<String, Object>) obj));
      } else if (obj instanceof List) {
        arr.add(convertList((List<?>)obj));
      } else if (obj instanceof CharSequence) {
        arr.add(obj.toString());
      } else if (obj instanceof BigDecimal) {
        arr.add(((BigDecimal) obj).doubleValue());
      } else if (obj instanceof BigInteger) {
        arr.add(((BigInteger) obj).longValue());
      } else if (obj == null || obj instanceof Number || obj instanceof Boolean) {
        arr.add(obj);
      } else {
        throw new VertxException("Cannot have objects of class " + obj.getClass() +" in JSON");
      }
    }
    return arr;
  }

}