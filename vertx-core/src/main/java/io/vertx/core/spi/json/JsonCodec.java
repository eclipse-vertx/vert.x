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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface JsonCodec {

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

  /**
   * Decode JSON from the given {@link InputStream} to an object of the specified type.
   * <p>
   * The input is expected to be UTF-8 encoded.
   * <p>
   * The codec does not close or flush the stream; the caller retains ownership.
   *
   * @param in    the input stream containing UTF-8 encoded JSON
   * @param clazz the required object's class
   * @return the decoded instance
   * @throws DecodeException anything preventing the decoding
   */
  default <T> T fromInputStream(InputStream in, Class<T> clazz) throws DecodeException {
    try {
      return fromBuffer(Buffer.buffer(in.readAllBytes()), clazz);
    } catch (IOException e) {
      throw new DecodeException(e.getMessage(), e);
    }
  }

  /**
   * Decode JSON from the given {@link InputStream}.
   * <p>
   * The input is expected to be UTF-8 encoded.
   * <p>
   * The codec does not close or flush the stream; the caller retains ownership.
   *
   * @param in the input stream containing UTF-8 encoded JSON
   * @return a JSON element which can be a {@link JsonArray}, {@link JsonObject}, {@link String}, etc.
   * @throws DecodeException anything preventing the decoding
   */
  default Object fromInputStream(InputStream in) throws DecodeException {
    return fromInputStream(in, Object.class);
  }

  /**
   * Encode the specified object as JSON to the given {@link OutputStream}.
   * <p>
   * The output is UTF-8 encoded.
   * <p>
   * The codec does not close or flush the stream; the caller retains ownership.
   *
   * @param object the object to encode
   * @param out    the output stream to write to
   * @throws EncodeException anything preventing the encoding
   */
  default void toOutputStream(Object object, OutputStream out) throws EncodeException {
    try {
      out.write(toBuffer(object).getBytes());
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }
}
