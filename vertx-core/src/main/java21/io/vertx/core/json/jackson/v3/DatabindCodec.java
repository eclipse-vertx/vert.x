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

package io.vertx.core.json.jackson.v3;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DatabindCodec extends JacksonCodec {

  private static final ObjectMapper mapper = JsonMapper
    .builder(JacksonCodec.factory)
    .addModule(new VertxModule())
    .build();

  /**
   * @return the {@link ObjectMapper} used for data binding.
   */
  public static ObjectMapper mapper() {
    return mapper;
  }

  @Override
  public <T> T fromValue(Object json, Class<T> clazz) {
    T value = DatabindCodec.mapper.convertValue(json, clazz);
    if (clazz == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  public <T> T fromValue(Object json, TypeReference<T> type) {
    T value = DatabindCodec.mapper.convertValue(json, type);
    if (type.getType() == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  @Override
  public <T> T fromString(String str, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(str), clazz);
  }

  public <T> T fromString(String str, TypeReference<T> typeRef) throws DecodeException {
    return fromParser(createParser(str), typeRef);
  }

  @Override
  public <T> T fromBuffer(Buffer buf, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(buf), clazz);
  }

  public <T> T fromBuffer(Buffer buf, TypeReference<T> typeRef) throws DecodeException {
    return fromParser(createParser(buf), typeRef);
  }

  public static JsonParser createParser(BufferInternal buf) {
    return DatabindCodec.mapper.createParser((InputStream) new ByteBufInputStream(buf.getByteBuf()));
  }

  public static JsonParser createParser(String str) {
    return DatabindCodec.mapper.createParser(str);
  }

  public static <T> T fromParser(JsonParser parser, Class<T> type) throws DecodeException {
    T value;
    JsonToken remaining;
    try {
      value = DatabindCodec.mapper.readValue(parser, type);
      remaining = parser.nextToken();
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    } finally {
      close(parser);
    }
    if (remaining != null) {
      throw new DecodeException("Unexpected trailing token");
    }
    if (type == Object.class) {
      value = (T) adapt(value);
    }
    return value;
  }

  private static <T> T fromParser(JsonParser parser, TypeReference<T> type) throws DecodeException {
    T value;
    try {
      value = DatabindCodec.mapper.readValue(parser, type);
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

  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    try {
      String result;
      if (pretty) {
        result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
      } else {
        result = mapper.writeValueAsString(object);
      }
      return result;
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    try {
      byte[] result;
      if (pretty) {
        result = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(object);
      } else {
        result = mapper.writeValueAsBytes(object);
      }
      return Buffer.buffer(result);
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
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
}
