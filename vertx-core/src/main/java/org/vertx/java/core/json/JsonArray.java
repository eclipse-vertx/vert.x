/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.json;

import org.vertx.java.core.http.impl.ws.Base64;
import org.vertx.java.core.json.impl.Json;

import java.util.*;

/**
 * Represents a JSON array
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArray extends JsonElement implements Iterable<Object> {

  final List<Object> list;

  public JsonArray(List<Object> array) {
    this.list = array;
  }

  public JsonArray(Object[] array) {
    this.list = Arrays.asList(array);
  }

  public JsonArray() {
    this.list = new ArrayList<>();
  }

  @SuppressWarnings("unchecked")
  public JsonArray(String jsonString) {
    list = (List<Object>) Json.decodeValue(jsonString, List.class);
  }

  public JsonArray addString(String str) {
    list.add(str);
    return this;
  }

  public JsonArray addObject(JsonObject value) {
    list.add(value.map);
    return this;
  }

  public JsonArray addArray(JsonArray value) {
    list.add(value.list);
    return this;
  }

  public JsonArray addElement(JsonElement value) {
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
    String encoded = Base64.encodeBytes(value);
    list.add(encoded);
    return this;
  }

  public JsonArray add(Object obj) {
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

  public Object get(final int index) {
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

  public JsonArray copy() {
    return new JsonArray(encode());
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
  private static Object convertObject(final Object obj) {
    Object retVal = obj;

    if (obj != null) {
      if (obj instanceof List) {
        retVal = new JsonArray((List<Object>) obj);
      } else if (obj instanceof Map) {
        retVal = new JsonObject((Map<String, Object>) obj);
      }
    }

    return retVal;
  }
}
