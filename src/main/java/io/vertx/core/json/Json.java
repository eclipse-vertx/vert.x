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

package io.vertx.core.json;

import io.vertx.core.ServiceHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.jackson.JacksonFactory;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Json {

  /**
   *
   */
  public static final JsonCodec CODEC = JsonFactory.INSTANCE.codec();

  /**
   * Load the factory with the {@code ServiceLoader}, when no factory is found then a factory
   * using Jackson will be returned.
   * <br/>
   * When {@code jackson-databind} is available then a codec using it will be used otherwise
   * the codec will only use {@code jackson-core} and provide best effort mapping.
   */
  public static io.vertx.core.spi.JsonFactory load() {
    io.vertx.core.spi.JsonFactory factory = ServiceHelper.loadFactoryOrNull(io.vertx.core.spi.JsonFactory.class);
    if (factory == null) {
      factory = JacksonFactory.INSTANCE;
    }
    return factory;
  }

  /**
   * Encode a POJO to JSON using the underlying Jackson mapper.
   *
   * @param obj a POJO
   * @return a String containing the JSON representation of the given POJO.
   * @throws EncodeException if a property cannot be encoded.
   */
  public static String encode(Object obj) throws EncodeException {
    return CODEC.toString(obj);
  }

  /**
   * Encode a POJO to JSON using the underlying Jackson mapper.
   *
   * @param obj a POJO
   * @return a Buffer containing the JSON representation of the given POJO.
   * @throws EncodeException if a property cannot be encoded.
   */
  public static Buffer encodeToBuffer(Object obj) throws EncodeException {
    return CODEC.toBuffer(obj);
  }

  /**
   * Encode a POJO to JSON with pretty indentation, using the underlying Jackson mapper.
   *
   * @param obj a POJO
   * @return a String containing the JSON representation of the given POJO.
   * @throws EncodeException if a property cannot be encoded.
   */
  public static String encodePrettily(Object obj) throws EncodeException {
    return CODEC.toString(obj, true);
  }

  /**
   * Decode a given JSON string to a POJO of the given class type.
   * @param str the JSON string.
   * @param clazz the class to map to.
   * @param <T> the generic type.
   * @return an instance of T
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  public static <T> T decodeValue(String str, Class<T> clazz) throws DecodeException {
    return CODEC.fromString(str, clazz);
  }

  /**
   * Decode a given JSON string.
   *
   * @param str the JSON string.
   *
   * @return a JSON element which can be a {@link JsonArray}, {@link JsonObject}, {@link String}, ...etc if the content is an array, object, string, ...etc
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  public static Object decodeValue(String str) throws DecodeException {
    return decodeValue(str, Object.class);
  }

  /**
   * Decode a given JSON buffer.
   *
   * @param buf the JSON buffer.
   *
   * @return a JSON element which can be a {@link JsonArray}, {@link JsonObject}, {@link String}, ...etc if the buffer contains an array, object, string, ...etc
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  public static Object decodeValue(Buffer buf) throws DecodeException {
    return decodeValue(buf, Object.class);
  }

  /**
   * Decode a given JSON buffer to a POJO of the given class type.
   * @param buf the JSON buffer.
   * @param clazz the class to map to.
   * @param <T> the generic type.
   * @return an instance of T
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  public static <T> T decodeValue(Buffer buf, Class<T> clazz) throws DecodeException {
    return CODEC.fromBuffer(buf, clazz);
  }

  /**
   * Convert a base64url encoded string to a base64 encoded string.
   *
   * Example:
   * <code>
   *     toBase64("qL8R4QIcQ_ZsRqOAbeRfcZhilN_MksRtDaErMA");
   *     "qL8R4QIcQ/ZsRqOAbeRfcZhilN/MksRtDaErMA=="
   * </code>
   *
   * JsonObject and JsonArray implement the RFC-7493 which defines how binary data is encoded. However
   * the implementation on Vert.x 3.x was incorrect and did not use the correct encoding <code>base64url</code>.
   *
   * In order to interact with older Vert.x applications or other systems that use plain Base64 encoding this
   * utility method will convert base64url strings to base64.
   *
   * @param base64url base64 url without padding string.
   * @return base64 with padding encoded string.
   */
  public static String toBase64(String base64url) {
    // padding is at most 3 '=' characters
    final String padding = "===";
    int stringLength = base64url.length();
    int diff = stringLength % 4;
    // if the modulus is 0 then no padding is needed
    if (diff != 0) {
      // padding needed, just append the difference to 4 of the modulus with '=' characters.
      base64url = base64url + padding.substring(0, 4 - diff);
    }
    // replace the characters that are modified in the base64 alphabets.
    return base64url
      .replace('-', '+')
      .replace('_', '/');
  }
}
