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

package io.vertx.core.json.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.json.JsonCodec;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonCodec implements JsonCodec {

  @SuppressWarnings("deprecation")
  @Override
  public <T> T fromValue(Object json, Class<T> clazz) {
    T value = Json.mapper.convertValue(json, clazz);
    if (clazz == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  @SuppressWarnings("deprecation")
  public static <T> T fromValue(Object json, TypeReference<T> type) {
    T value = Json.mapper.convertValue(json, type);
    if (type.getType() == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  @Override
  public <T> T fromString(String str, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(str), clazz);
  }

  public static <T> T fromString(String str, TypeReference<T> type) throws DecodeException {
    return fromParser(createParser(str), type);
  }

  @Override
  public <T> T fromBuffer(Buffer buf, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(buf), clazz);
  }

  public static <T> T fromBuffer(Buffer buf, TypeReference<T> type) throws DecodeException {
    return fromParser(createParser(buf), type);
  }

  @SuppressWarnings("deprecation")
  private static JsonParser createParser(Buffer buf) {
    try {
      return Json.mapper.getFactory().createParser((InputStream) new ByteBufInputStream(buf.getByteBuf()));
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  @SuppressWarnings("deprecation")
  private static JsonParser createParser(String str) {
    try {
      return Json.mapper.getFactory().createParser(str);
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  @SuppressWarnings("deprecation")
  private static <T> T fromParser(JsonParser parser, Class<T> type) throws DecodeException {
    T value;
    try {
      value = Json.mapper.readValue(parser, type);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    } finally {
      close(parser);
    }
    if (type == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  @SuppressWarnings("deprecation")
  private static <T> T fromParser(JsonParser parser, TypeReference<T> type) throws DecodeException {
    T value;
    try {
      value = Json.mapper.readValue(parser, type);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    } finally {
      close(parser);
    }
    if (type.getType() == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  private static void close(JsonParser parser) {
    try {
      parser.close();
    } catch (IOException ignore) {
    }
  }

  private static Object adapt(Object o) {
    try {
      if (o instanceof List) {
        List list = (List) o;
        return new JsonArray(list);
      } else if (o instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) o;
        return new JsonObject(map);
      }
      return o;
    } catch (Exception e) {
      throw new DecodeException("Failed to decode: " + e.getMessage());
    }
  }


  @SuppressWarnings("deprecation")
  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    try {
      ObjectMapper mapper = pretty ? Json.prettyMapper : Json.mapper;
      return mapper.writeValueAsString(object);
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    try {
      ObjectMapper mapper = pretty ? Json.prettyMapper : Json.mapper;
      return Buffer.buffer(mapper.writeValueAsBytes(object));
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  /**
   * Decode a given JSON string to a POJO of the given type.
   * @param str the JSON string.
   * @param type the type to map to.
   * @param <T> the generic type.
   * @return an instance of T
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  public static <T> T decodeValue(String str, TypeReference<T> type) throws DecodeException {
    return JacksonCodec.fromString(str, type);
  }

  /**
   * Decode a given JSON buffer to a POJO of the given class type.
   * @param buf the JSON buffer.
   * @param type the type to map to.
   * @param <T> the generic type.
   * @return an instance of T
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  public static <T> T decodeValue(Buffer buf, TypeReference<T> type) throws DecodeException {
    return JacksonCodec.fromBuffer(buf, type);
  }
}
