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
import org.vertx.java.core.json.impl.Json;

import java.util.*;

/**
 * Represents a JSON array.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArray extends JsonElement implements Iterable<Object> {

  protected List list;

  public JsonArray(List list) {
    this(list, true);
  }

  public JsonArray(Object[] array) {
    this(Arrays.asList(array), true);
  }

  protected JsonArray(List list, boolean copy) {
    this.list = copy ? convertList(list): list;
  }

  public JsonArray() {
    this.list = new ArrayList<>();
  }

  public JsonArray(String jsonString) {
    list = Json.decodeValue(jsonString, List.class);
  }

  public JsonArray addString(String str) {
    list.add(str);
    return this;
  }

  public JsonArray addObject(JsonObject value) {
    list.add(value == null ? null : value.map);
    return this;
  }

  public JsonArray addArray(JsonArray value) {
    list.add(value == null ? null : value.list);
    return this;
  }

  public JsonArray addElement(JsonElement value) {
    if (value == null) {
      list.add(null);
      return this;
    }
    if (value.isArray()) {
      return addArray(value.asArray());
    }
    return addObject(value.asObject());
  }

  public JsonArray addNumber(Number value) {
    list.add(value);
    return this;
  }

  public JsonArray addBoolean(Boolean value) {
    list.add(value);
    return this;
  }

  public JsonArray addBinary(byte[] value) {
    String encoded = (value == null) ? null : org.vertx.java.core.json.impl.Base64.encodeBytes(value);
    list.add(encoded);
    return this;
  }

  public JsonArray add(Object value) {
    if (value == null) {
      list.add(null);
    } else if (value instanceof JsonObject) {
      addObject((JsonObject) value);
    } else if (value instanceof JsonArray) {
      addArray((JsonArray) value);
    } else if (value instanceof String) {
      addString((String) value);
    } else if (value instanceof Number) {
      addNumber((Number) value);
    } else if (value instanceof Boolean) {
      addBoolean((Boolean)value);
    } else if (value instanceof byte[]) {
      addBinary((byte[])value);
    } else {
      throw new VertxException("Cannot add objects of class " + value.getClass() +" to JsonArray");
    }
    return this;
  }

  public int size() {
    return list.size();
  }

  public <T> T get(final int index) {
    return convertObject(list.get(index));
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<Object>() {

      Iterator<Object> iter = list.iterator();

      @Override
      public boolean hasNext() {
        return iter.hasNext();
      }

      @Override
      public Object next() {
        return convertObject(iter.next());
      }

      @Override
      public void remove() {
        iter.remove();
      }
    };
  }

  public boolean contains(Object value) {
    return list.contains(value);
  }

  public String encode() throws EncodeException {
    return Json.encode(this.list);
  }

  public String encodePrettily() throws EncodeException {
    return Json.encodePrettily(this.list);
  }

  /**
   *
   * @return a copy of the JsonArray
   */
  public JsonArray copy() {
    return new JsonArray(list, true);
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

    JsonArray that = (JsonArray) o;

    if (this.list.size() != that.list.size())
      return false;

    Iterator<?> iter = that.list.iterator();
    for (Object entry : this.list) {
      Object other = iter.next();
      if (entry == null) {
        if (other != null) {
          return false;
        }
      } else if (!equals(entry, other)) {
        return false;
      }
    }
    return true;
  }

  public Object[] toArray() {
    return convertList(list).toArray();
  }

  public List toList() {
    return convertList(list);
  }

  @SuppressWarnings("unchecked")
  private <T> T convertObject(final Object obj) {
    Object retVal = obj;
    if (obj != null) {
      if (obj instanceof List) {
        retVal = new JsonArray((List<Object>) obj, false);
      } else if (obj instanceof Map) {
        retVal = new JsonObject((Map<String, Object>) obj, false);
      }
    }
    return (T)retVal;
  }
}
