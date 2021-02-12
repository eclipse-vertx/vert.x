/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.json;

import io.vertx.core.ServiceHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.jackson.JacksonCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface JsonCodec {

  /**
   * <p> Load the JSON codec with the {@code ServiceLoader}
   *
   * <ul>
   *   <li>An attempt is made to load a factory using the service loader {@code META-INF/services} {@link JsonCodec}.</li>
   *   <li>Codecs are sorted </li>
   *   <li>If no codec is resolved (which is usually the default case), the a {@link JacksonCodec} is created and used.</li>
   * </ul>
   */
  static JsonCodec loadCodec() {
    List<JsonCodec> factories = new ArrayList<>(ServiceHelper.loadFactories(JsonCodec.class));
    factories.sort(Comparator.comparingInt(JsonCodec::order));
    if (factories.size() > 0) {
      return factories.iterator().next();
    } else {
      return JacksonCodecLoader.loadJacksonCodec();
    }
  }

  /**
   * Static cached codec instance, initialized with {@link #loadCodec()}.
   */
  JsonCodec INSTANCE = loadCodec();

  /**
   * The order of the codec. If there is more than one matching codec they will be tried in ascending order.
   *
   * @implSpec returns {@link Integer#MAX_VALUE}
   *
   * @return  the order
   */
  default int order() {
    return Integer.MAX_VALUE;
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
