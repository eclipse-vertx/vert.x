/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An internal conversion helper, later it could be merged with JsonObject mapFrom/mapTo and moved in Json class
 */
public class ConversionHelper {

  @SuppressWarnings("unchecked")
  public static Object toObject(Object obj) {
    if (obj instanceof Map) {
      return toJsonObject((Map<String, Object>) obj);
    } else if (obj instanceof List) {
      return toJsonArray((List<Object>) obj);
    } else if (obj instanceof CharSequence) {
      return obj.toString();
    }
    return obj;
  }

  @SuppressWarnings("unchecked")
  private static Object toJsonElement(Object obj) {
    if (obj instanceof Map) {
      return toJsonObject((Map<String, Object>) obj);
    } else if (obj instanceof List) {
      return toJsonArray((List<Object>) obj);
    } else if (obj instanceof CharSequence) {
      return obj.toString();
    } else if (obj instanceof Buffer) {
      return Base64.getEncoder().encodeToString(((Buffer)obj).getBytes());
    }
    return obj;
  }

  public static JsonObject toJsonObject(Map<String, Object> map) {
    if (map == null) {
      return null;
    }
    map = new LinkedHashMap<>(map);
    map.entrySet().forEach(e -> e.setValue(toJsonElement(e.getValue())));
    return new JsonObject(map);
  }

  public static JsonArray toJsonArray(List<Object> list) {
    if (list == null) {
      return null;
    }
    list = new ArrayList<>(list);
    for (int i = 0;i < list.size();i++) {
      list.set(i, toJsonElement(list.get(i)));
    }
    return new JsonArray(list);
  }

  @SuppressWarnings("unchecked")
  public static <T> T fromObject(Object obj) {
    if (obj instanceof JsonObject) {
      return (T)fromJsonObject((JsonObject)obj);
    } else if (obj instanceof JsonArray) {
      return (T)fromJsonArray((JsonArray)obj);
    }
    return (T)obj;
  }

  public static Map<String, Object> fromJsonObject(JsonObject json) {
    if (json == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>(json.getMap());
    map.entrySet().forEach(entry -> {
      entry.setValue(fromObject(entry.getValue()));
    });
    return map;
  }

  public static List<Object> fromJsonArray(JsonArray json) {
    if (json == null) {
      return null;
    }
    List<Object> list = new ArrayList<>(json.getList());
    for (int i = 0;i < list.size();i++) {
      list.set(i, fromObject(list.get(i)));
    }
    return list;
  }
}
