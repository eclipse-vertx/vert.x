/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.*;

import static io.vertx.core.json.impl.JsonUtil.BASE64_ENCODER;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

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
      return BASE64_ENCODER.encodeToString(((Buffer) obj).getBytes());
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
    for (int i = 0; i < list.size(); i++) {
      list.set(i, toJsonElement(list.get(i)));
    }
    return new JsonArray(list);
  }

  @SuppressWarnings("unchecked")
  public static <T> T fromObject(Object obj) {
    if (obj instanceof JsonObject) {
      return (T) fromJsonObject((JsonObject) obj);
    } else if (obj instanceof JsonArray) {
      return (T) fromJsonArray((JsonArray) obj);
    } else if (obj instanceof Instant) {
      return (T) ISO_INSTANT.format((Instant) obj);
    } else if (obj instanceof byte[]) {
      return (T) BASE64_ENCODER.encodeToString((byte[]) obj);
    } else if (obj instanceof Enum) {
      return (T) ((Enum) obj).name();
    }

    return (T) obj;
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
    for (int i = 0; i < list.size(); i++) {
      list.set(i, fromObject(list.get(i)));
    }
    return list;
  }
}
