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

package io.vertx.core.json.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.JsonFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonJsonFactory implements JsonFactory {

  @Override
  public <T> T fromValue(Object json, Class<T> clazz) {
    return fromValue(json, Json.mapper.getTypeFactory().constructType(clazz));
  }

  @Override
  public <T> T fromString(String str, Class<T> clazz) throws DecodeException {
    return fromString(str, Json.mapper.getTypeFactory().constructType(clazz));
  }

  @Override
  public <T> T fromBuffer(Buffer buf, Class<T> clazz) throws DecodeException {
    return fromBuffer(buf, Json.mapper.getTypeFactory().constructType(clazz));
  }

  public static <T> T fromString(String str, JavaType type) throws DecodeException {
    T value;
    try {
      value = Json.mapper.readValue(str, type);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
    if (type.isJavaLangObject()) {
      value = (T) adapt(value);
    }
    return value;
  }

  public static <T> T fromBuffer(Buffer buf, JavaType type) throws DecodeException {
    T value;
    try {
      value = Json.mapper.readValue((InputStream) new ByteBufInputStream(buf.getByteBuf()), type);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
    if (type.isJavaLangObject()) {
      value = (T) adapt(value);
    }
    return value;
  }

  public static <T> T fromValue(Object json, TypeReference<T> type) {
    return fromValue(json, Json.mapper.getTypeFactory().constructType(type));
  }

  public static <T> T fromValue(Object json, JavaType type) {
    T value = Json.mapper.convertValue(json, type);
    if (type.isJavaLangObject()) {
      value = (T) adapt(value);
    }
    return value;
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


  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    try {
      ObjectMapper mapper = pretty ? Json.prettyMapper : Json.mapper;
      return mapper.writeValueAsString(object);
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    try {
      ObjectMapper mapper = pretty ? Json.prettyMapper : Json.mapper;
      return Buffer.buffer(mapper.writeValueAsBytes(object));
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }
}
