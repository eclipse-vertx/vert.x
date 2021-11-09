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

package io.vertx.core.spi.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * JsonCodec SPI interface. Implementations of this interface provide custom JSON encoding and decoding support for the
 * running vert.x application.
 *
 * The SPI consists of 3 main features:
 *
 * <ol>
 *   <li>{@link #fromString(String, Class)}, {@link #fromBuffer(Buffer, Class)} - Parse a given test or binary input and
 *   return a object representation of the input for the given {@link Class} type</li>
 *   <li>{@link #fromValue(Object, Class)} - Given an object, use the mapping features if available to convert to the desired target POJO of {@link Class}</li>
 *   <li>{@link #toString(Object)}, {@link #toBuffer(Object)} - Encodes a given object to either a textual or binary representation.</li>
 * </ol>
 *
 * The SPI assumes the following decoding rules (when mapping to POJO):
 *
 * <ul>
 *   <li>When the target class is {@link java.time.Instant}, the input is expected to be in {@link java.time.format.DateTimeFormatter#ISO_DATE} format.</li>
 *   <li>When the target class is {@code byte[]} or {@link Buffer}, the input is expected to be in {@code Base64URL} string format.</li>
 * </ul>
 *
 * The following rules are expected when encoding:
 *
 * <ul>
 *   <li>When {@code object} is of type {@link java.time.Instant}, the output is expected to be in {@link java.time.format.DateTimeFormatter#ISO_DATE} format.</li>
 *   <li>When {@code object} is of type {@code byte[]} or {@link Buffer}, the output is expected to be in {@code Base64URL} format.</li>
 *   <li>When {@code object} is of type {@link JsonObject}, the output is expected to be the object internal map {@link JsonObject#getMap()}.</li>
 *   <li>When {@code object} is of type {@link JsonArray}, the output is expected to be the object internal list {@link JsonArray#getList()}.</li>
 * </ul>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface JsonCodec {

  /**
   * Decode the provided {@code json} string to an object extending {@code clazz}.
   *
   * @param json the json string
   * @param clazz the required object's class
   * @return the instance
   * @throws DecodeException anything preventing the decoding
   */
  <T> T fromString(String json, Class<T> clazz) throws DecodeException;

  /**
   * Like {@link #fromString(String, Class)} but with a json {@link Buffer}
   */
  <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException;

  /**
   * Like {@link #fromString(String, Class)} but with a json {@code Object}
   */
  <T> T fromValue(Object json, Class<T> toValueType);

  /**
   * Encode the specified {@code object} to a compact (non-pretty) string.
   *
   * See {@link #toString(Object, boolean)}.
   *
   * @param object the object to encode
   * @return the json encoded string
   * @throws DecodeException anything preventing the encoding
   */
  default String toString(Object object) throws EncodeException {
    return toString(object, false);
  }

  /**
   * Encode the specified {@code object} to a string.
   *
   * Implementations <b>MUST</b> follow the requirements:
   *
   * <ul>
   *   <li>When {@code object} is of type {@link java.time.Instant}, the output is expected to be in {@link java.time.format.DateTimeFormatter#ISO_DATE} format.</li>
   *   <li>When {@code object} is of type {@code byte[]} or {@link Buffer}, the output is expected to be in {@code Base64URL} format.</li>
   *   <li>When {@code object} is of type {@link JsonObject}, the output is expected to be the object internal map {@link JsonObject#getMap()}.</li>
   *   <li>When {@code object} is of type {@link JsonArray}, the output is expected to be the object internal list {@link JsonArray#getList()}.</li>
   * </ul>
   *
   * @param object the object to encode
   * @param pretty {@code true} to format the string prettily
   * @return the json encoded string
   * @throws DecodeException anything preventing the encoding
   */
  String toString(Object object, boolean pretty) throws EncodeException;

  /**
   * Encode the specified {@code object} to a {@link Buffer}.
   *
   * Implementations <b>MUST</b> follow the requirements:
   *
   * <ul>
   *   <li>When {@code object} is of type {@link java.time.Instant}, the output is expected to be in {@link java.time.format.DateTimeFormatter#ISO_DATE} format.</li>
   *   <li>When {@code object} is of type {@code byte[]} or {@link Buffer}, the output is expected to be in {@code Base64URL} format.</li>
   *   <li>When {@code object} is of type {@link JsonObject}, the output is expected to be the object internal map {@link JsonObject#getMap()}.</li>
   *   <li>When {@code object} is of type {@link JsonArray}, the output is expected to be the object internal list {@link JsonArray#getList()}.</li>
   * </ul>
   *
   * @param object the object to encode
   * @param pretty {@code true} to format the string prettily
   * @return the json encoded string
   * @throws DecodeException anything preventing the encoding
   */
  Buffer toBuffer(Object object, boolean pretty) throws EncodeException;

  /**
   * Encode the specified {@code object} to a compact (non-pretty) {@link Buffer}.
   *
   * See {@link #toBuffer(Object, boolean)}.
   *
   * @param object the object to encode
   * @return the json encoded string
   * @throws DecodeException anything preventing the encoding
   */
  default Buffer toBuffer(Object object) throws EncodeException {
    return toBuffer(object, false);
  }
}
