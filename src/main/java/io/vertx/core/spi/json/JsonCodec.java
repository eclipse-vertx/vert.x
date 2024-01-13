/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface JsonCodec {

  /**
   * Decode a given JSON string.
   *
   * @param json the JSON string.
   * @param wrap
   * @return a JSON element which can be a {@link JsonArray}, {@link JsonObject}, {@link String}, ...etc. if the string contains an array, object, string, ...etc
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  default Object fromString(String json, boolean wrap) throws DecodeException {
    Object value = fromString(json, Object.class);
    return wrap ? wrap(value) : value;
  }

  /**
   * Wrap Java {@link Map} in a {@link JsonObject},{@link List} in {@link JsonArray}.
   * Otherwise, return the {@code value} unchanged.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  default Object wrap(Object value) {
    if (value instanceof Map) {
      return new JsonObject((Map<String, Object>) value);
    }
    if (value instanceof List) {
      return new JsonArray((List) value);
    }
    return value;
  }

  /**
   * Decode the provide {@code json} string to an object extending {@code clazz}.
   *
   * @param json the json string
   * @param clazz the required object's class
   * @return the instance
   * @throws DecodeException anything preventing the decoding
   */
  <T> T fromString(String json, Class<T> clazz) throws DecodeException;

  /**
   * Like {@link #fromString(String, boolean)} but with a json {@link Buffer}
   */
  default Object fromBuffer(Buffer json, boolean wrap) throws DecodeException {
    Object value = fromBuffer(json, Object.class);
    return wrap ? wrap(value) : value;
  }

  /**
   * Like {@link #fromString(String, Class)} but with a json {@link Buffer}
   */
  <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException;

  /**
   * Like {@link #fromString(String, Class)} but with a json {@code Object}
   */
  <T> T fromValue(Object json, Class<T> toValueType);

  /**
   * Encode the specified {@code object} to a string.
   */
  default String toString(Object object) throws EncodeException {
    return toString(object, false);
  }

  /**
   * Encode the specified {@code object} to a string.
   *
   * @param object the object to encode
   * @param pretty {@code true} to format the string prettily
   * @return the json encoded string
   * @throws DecodeException anything preventing the encoding
   */
  String toString(Object object, boolean pretty) throws EncodeException;

  /**
   * Like {@link #toString(Object, boolean)} but with a json {@link Buffer}
   */
  Buffer toBuffer(Object object, boolean pretty) throws EncodeException;

  /**
   * Like {@link #toString(Object)} but with a json {@link Buffer}
   */
  default Buffer toBuffer(Object object) throws EncodeException {
    return toBuffer(object, false);
  }
}
