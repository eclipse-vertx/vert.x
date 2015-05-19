/*
 * Copyright (c) 2011-2014 The original author or authors
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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.impl.Json;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A representation of a <a href="http://json.org/">JSON</a> array in Java.
 * <p>
 * Unlike some other languages Java does not have a native understanding of JSON. To enable JSON to be used easily
 * in Vert.x code we use this class to encapsulate the notion of a JSON array.
 * <p>
 * Please see the documentation for more information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArray implements Iterable<Object>, ClusterSerializable {

  private List<Object> list;

  /**
   * Create an instance from a String of JSON
   *
   * @param json the string of JSON
   */
  public JsonArray(String json) {
    fromJson(json);
  }

  /**
   * Create an empty instance
   */
  public JsonArray() {
    list = new ArrayList<>();
  }

  /**
   * Create an instance from a List. The List is not copied.
   *
   * @param list
   */
  public JsonArray(List list) {
    this.list = list;
  }

  /**
   * Get the String at position {@code pos} in the array,
   *
   * @param pos  the position in the array
   * @return  the String, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to String
   */
  public String getString(int pos) {
    CharSequence cs = (CharSequence)list.get(pos);
    return cs == null ? null : cs.toString();
  }

  /**
   * Get the Integer at position {@code pos} in the array,
   *
   * @param pos  the position in the array
   * @return  the Integer, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to Integer
   */
  public Integer getInteger(int pos) {
    Number number = (Number)list.get(pos);
    if (number == null) {
      return null;
    } else if (number instanceof Integer) {
      return (Integer)number; // Avoids unnecessary unbox/box
    } else {
      return number.intValue();
    }
  }

  /**
   * Get the Long at position {@code pos} in the array,
   *
   * @param pos  the position in the array
   * @return  the Long, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to Long
   */
  public Long getLong(int pos) {
    Number number = (Number)list.get(pos);
    if (number == null) {
      return null;
    } else if (number instanceof Long) {
      return (Long)number; // Avoids unnecessary unbox/box
    } else {
      return number.longValue();
    }
  }

  /**
   * Get the Double at position {@code pos} in the array,
   *
   * @param pos  the position in the array
   * @return  the Double, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to Double
   */
  public Double getDouble(int pos) {
    Number number = (Number)list.get(pos);
    if (number == null) {
      return null;
    } else if (number instanceof Double) {
      return (Double)number; // Avoids unnecessary unbox/box
    } else {
      return number.doubleValue();
    }
  }

  /**
   * Get the Float at position {@code pos} in the array,
   *
   * @param pos  the position in the array
   * @return  the Float, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to Float
   */
  public Float getFloat(int pos) {
    Number number = (Number)list.get(pos);
    if (number == null) {
      return null;
    } else if (number instanceof Float) {
      return (Float)number; // Avoids unnecessary unbox/box
    } else {
      return number.floatValue();
    }
  }

  /**
   * Get the Boolean at position {@code pos} in the array,
   *
   * @param pos  the position in the array
   * @return  the Boolean, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to Integer
   */
  public Boolean getBoolean(int pos) {
    return (Boolean)list.get(pos);
  }

  /**
   * Get the JsonObject at position {@code pos} in the array.
   *
   * @param pos  the position in the array
   * @return  the Integer, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to JsonObject
   */
  public JsonObject getJsonObject(int pos) {
    Object val = list.get(pos);
    if (val instanceof Map) {
      val = new JsonObject((Map)val);
    }
    return (JsonObject)val;
  }

  /**
   * Get the JsonArray at position {@code pos} in the array.
   *
   * @param pos  the position in the array
   * @return  the Integer, or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to JsonArray
   */
  public JsonArray getJsonArray(int pos) {
    Object val = list.get(pos);
    if (val instanceof List) {
      val = new JsonArray((List)val);
    }
    return (JsonArray)val;
  }

  /**
   * Get the byte[] at position {@code pos} in the array.
   * <p>
   * JSON itself has no notion of a binary, so this method assumes there is a String value and
   * it contains a Base64 encoded binary, which it decodes if found and returns.
   * <p>
   * This method should be used in conjunction with {@link #add(byte[])}
   *
   * @param pos  the position in the array
   * @return  the byte[], or null if a null value present
   * @throws java.lang.ClassCastException if the value cannot be converted to String
   */
  public byte[] getBinary(int pos) {
    String val = (String)list.get(pos);
    if (val == null) {
      return null;
    } else {
      return Base64.getDecoder().decode(val);
    }
  }

  /**
   * Get the Object value at position {@code pos} in the array.
   *
   * @param pos  the position in the array
   * @return  the Integer, or null if a null value present
   */
  public Object getValue(int pos) {
    Object val = list.get(pos);
    if (val instanceof Map) {
      val = new JsonObject((Map)val);
    } else if (val instanceof List) {
      val = new JsonArray((List)val);
    }
    return val;
  }

  /**
   * Is there a null value at position pos?
   *
   * @param pos  the position in the array
   * @return true if null value present, false otherwise
   */
  public boolean hasNull(int pos) {
    return list.get(pos) == null;
  }

  /**
   * Add an enum to the JSON array.
   * <p>
   * JSON has no concept of encoding Enums, so the Enum will be converted to a String using the {@link java.lang.Enum#name}
   * method and the value added as a String.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Enum value) {
    Objects.requireNonNull(value);
    list.add(value.name());
    return this;
  }

  /**
   * Add a CharSequence to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(CharSequence value) {
    Objects.requireNonNull(value);
    list.add(value.toString());
    return this;
  }

  /**
   * Add a String to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(String value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add an Integer to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Integer value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add a Long to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Long value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add a Double to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Double value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add a Float to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Float value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add a Boolean to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Boolean value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add a null value to the JSON array.
   *
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray addNull() {
    list.add(null);
    return this;
  }

  /**
   * Add a JSON object to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(JsonObject value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add another JSON array to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(JsonArray value) {
    Objects.requireNonNull(value);
    list.add(value);
    return this;
  }

  /**
   * Add a binary value to the JSON array.
   * <p>
   * JSON has no notion of binary so the binary will be base64 encoded to a String, and the String added.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(byte[] value) {
    Objects.requireNonNull(value);
    list.add(Base64.getEncoder().encodeToString(value));
    return this;
  }

  /**
   * Add an Object to the JSON array.
   *
   * @param value  the value
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray add(Object value) {
    Objects.requireNonNull(value);
    value = Json.checkAndCopy(value, false);
    list.add(value);
    return this;
  }

  /**
   * Does the JSON array contain the specified value? This method will scan the entire array until it finds a value
   * or reaches the end.
   *
   * @param value  the value
   * @return  true if it contains the value, false if not
   */
  public boolean contains(Object value) {
    return list.contains(value);
  }

  /**
   * Remove the specified value from the JSON array. This method will scan the entire array until it finds a value
   * or reaches the end.
   *
   * @param value  the value to remove
   * @return true if it removed it, false if not found
   */
  public boolean remove(Object value) {
    return list.remove(value);
  }

  /**
   * Remove the value at the specified position in the JSON array.
   *
   * @param pos  the position to remove the value at
   * @return the removed value if removed, null otherwise
   */
  public Object remove(int pos) {
    return list.remove(pos);
  }

  /**
   * Get the number of values in this JSON array
   *
   * @return the number of items
   */
  public int size() {
    return list.size();
  }

  /**
   * Are there zero items in this JSON array?
   *
   * @return true if zero, false otherwise
   */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /**
   * Get the unerlying List
   *
   * @return  the underlying List
   */
  public List getList() {
    return list;
  }

  /**
   * Remove all entries from the JSON array
   *
   * @return  a reference to this, so the API can be used fluently
   */
  public JsonArray clear() {
    list.clear();
    return this;
  }

  /**
   * Get an Iterator over the values in the JSON array
   *
   * @return an iterator
   */
  @Override
  public Iterator<Object> iterator() {
    return new Iter(list.iterator());
  }

  /**
   * Encode the JSON array to a string
   *
   * @return the string encoding
   */
  public String encode() {
    return Json.encode(list);
  }

  /**
   * Encode the JSON array prettily as a string
   *
   * @return the string encoding
   */
  public String encodePrettily() {
    return Json.encodePrettily(list);
  }

  /**
   * Make a copy of the JSON array
   *
   * @return a copy
   */
  public JsonArray copy() {
    List<Object> copiedList = new ArrayList<>(list.size());
    for (Object val: list) {
      val = Json.checkAndCopy(val, true);
      copiedList.add(val);
    }
    return new JsonArray(copiedList);
  }

  /**
   * Get a Stream over the entries in the JSON array
   *
   * @return a Stream
   */
  public Stream<Object> stream() {
    return list.stream();
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
    return arrayEquals(list, o);
  }

  static boolean arrayEquals(List<?> l1, Object o2) {
    List<?> l2;
    if (o2 instanceof JsonArray) {
      l2 = ((JsonArray) o2).list;
    } else if (o2 instanceof List<?>) {
      l2 = (List<?>) o2;
    } else {
      return false;
    }
    if (l1.size() != l2.size())
      return false;
    Iterator<?> iter = l2.iterator();
    for (Object entry : l1) {
      Object other = iter.next();
      if (entry == null) {
        if (other != null) {
          return false;
        }
      } else if (!JsonObject.equals(entry, other)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  @Override
  public void writeToBuffer(Buffer buffer) {
    String encoded = encode();
    byte[] bytes = encoded.getBytes();
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    int start = pos + 4;
    String encoded = buffer.getString(start, start + length);
    fromJson(encoded);
    return pos + length + 4;
  }

  private void fromJson(String json) {
    list = Json.decodeValue(json, List.class);
  }

  private class Iter implements Iterator<Object> {

    final Iterator<Object> listIter;

    Iter(Iterator<Object> listIter) {
      this.listIter = listIter;
    }

    @Override
    public boolean hasNext() {
      return listIter.hasNext();
    }

    @Override
    public Object next() {
      Object val = listIter.next();
      if (val instanceof Map) {
        val = new JsonObject((Map)val);
      } else if (val instanceof List) {
        val = new JsonArray((List)val);
      }
      return val;
    }

    @Override
    public void remove() {
      listIter.remove();
    }
  }


}
