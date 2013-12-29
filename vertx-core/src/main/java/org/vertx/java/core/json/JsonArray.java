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

import org.vertx.java.core.json.impl.*;

import java.util.*;

/**
 * Represents a JSON array.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArray extends JsonElement implements Iterable<Object> {

  protected List<Object> list;

  protected void checkCopy() {
    if (needsCopy) {
      // deep copy the list lazily if the object is mutated
      list = convertList(list);
      needsCopy = false;
    }
  }

  public JsonArray(List<Object> array) {
    this.list = array;
  }

  public JsonArray(Object[] array) {
    this.list = Arrays.asList(array);
  }

  public JsonArray() {
    this.list = new ArrayList<>();
  }

  public JsonArray(String jsonString) {
    list = Json.decodeValue(jsonString, List.class);
  }

  public JsonArray addString(String str) {
    checkCopy();
    list.add(str);
    return this;
  }

  public JsonArray addObject(JsonObject value) {
    checkCopy();
    list.add(value.map);
    return this;
  }

  public JsonArray addArray(JsonArray value) {
    checkCopy();
    list.add(value.list);
    return this;
  }

  public JsonArray addElement(JsonElement value) {
    checkCopy();
    if (value.isArray()) {
      return addArray(value.asArray());
    }
    return addObject(value.asObject());
  }

  public JsonArray addNumber(Number value) {
    checkCopy();
    list.add(value);
    return this;
  }

  public JsonArray addBoolean(Boolean value) {
    checkCopy();
    list.add(value);
    return this;
  }

  public JsonArray addBinary(byte[] value) {
    checkCopy();
    String encoded = org.vertx.java.core.json.impl.Base64.encodeBytes(value);
    list.add(encoded);
    return this;
  }

  public JsonArray add(Object obj) {
    checkCopy();
    if (obj instanceof JsonObject) {
      obj = ((JsonObject) obj).map;
    } else if (obj instanceof JsonArray) {
      obj = ((JsonArray) obj).list;
    }
    list.add(obj);
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
    checkCopy();
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

  public JsonArray copy() {
    JsonArray copy = new JsonArray(list);
    copy.setNeedsCopy();
    return copy;
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
      if (!entry.equals(other)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(list);
  }

  public Object[] toArray() {
    return convertList(list).toArray();
  }

  @SuppressWarnings("unchecked")
  static List<Object> convertList(List<?> list) {
    List<Object> arr = new ArrayList<>(list.size());
    for (Object obj : list) {
      if (obj instanceof Map) {
        arr.add(JsonObject.convertMap((Map<String, Object>) obj));
      } else if (obj instanceof JsonObject) {
        arr.add(((JsonObject) obj).toMap());
      } else if (obj instanceof List) {
        arr.add(convertList((List<?>) obj));
      } else {
        arr.add(obj);
      }
    }
    return arr;
  }

  @SuppressWarnings("unchecked")
  private static <T> T convertObject(final Object obj) {
    Object retVal = obj;
    if (obj != null) {
      if (obj instanceof List) {
        retVal = new JsonArray((List<Object>) obj);
      } else if (obj instanceof Map) {
        retVal = new JsonObject((Map<String, Object>) obj);
      }
    }
    return (T)retVal;
  }
}
