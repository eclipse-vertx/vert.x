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

package io.vertx.core.json.pointer.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointerIterator;

import java.util.List;
import java.util.Map;

public class JsonPointerIteratorImpl implements JsonPointerIterator {

  @Override
  public boolean isObject(Object value) {
    return value instanceof JsonObject;
  }

  @Override
  public boolean isArray(Object value) {
    return value instanceof JsonArray;
  }

  @Override
  public boolean isNull(Object value) {
    return value == null;
  }

  @Override
  public boolean objectContainsKey(Object value, String key) {
    return isObject(value) && ((JsonObject)value).containsKey(key);
  }

  @Override
  public Object getObjectParameter(Object value, String key, boolean createOnMissing) {
    if (isObject(value)) {
      if (!objectContainsKey(value, key)) {
        if (createOnMissing) {
          writeObjectParameter(value, key, new JsonObject());
        } else {
          return null;
        }
      }
      return jsonifyValue(((JsonObject) value).getValue(key));
    }
    return null;
  }

  @Override
  public Object getArrayElement(Object value, int i) {
    if (isArray(value)) {
      try {
        return jsonifyValue(((JsonArray)value).getValue(i));
      } catch (IndexOutOfBoundsException ignored) {}
    }
    return null;
  }

  @Override
  public boolean writeObjectParameter(Object value, String key, Object el) {
    if (isObject(value)) {
      ((JsonObject)value).put(key, el);
      return true;
    } else return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean writeArrayElement(Object value, int i, Object el) {
    if (isArray(value)) {
      try {
        ((JsonArray)value).getList().add(i, el);
        return true;
      } catch (IndexOutOfBoundsException e) {
        return false;
      }
    } else return false;
  }

  @Override
  public boolean appendArrayElement(Object value, Object el) {
    if (isArray(value)) {
      ((JsonArray)value).add(el);
      return true;
    } else return false;
  }

  @SuppressWarnings("unchecked")
  private Object jsonifyValue(Object v) {
    if (v instanceof Map) return new JsonObject((Map<String, Object>)v);
    else if (v instanceof List) return new JsonArray((List)v);
    else return v;
  }
}
