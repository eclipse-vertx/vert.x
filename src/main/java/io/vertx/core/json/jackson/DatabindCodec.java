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

package io.vertx.core.json.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DatabindCodec extends JacksonCodec {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ObjectMapper prettyMapper = new ObjectMapper();

  static {
    initialize();
  }

  private static void initialize() {
    // Non-standard JSON but we allow C style comments in our JSON
    mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

    prettyMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    prettyMapper.configure(SerializationFeature.INDENT_OUTPUT, true);

    SimpleModule module = new SimpleModule();
    // custom types
    module.addSerializer(JsonObject.class, new JsonObjectSerializer());
    module.addSerializer(JsonArray.class, new JsonArraySerializer());
    // he have 2 extensions: RFC-7493
    module.addSerializer(Instant.class, new InstantSerializer());
    module.addDeserializer(Instant.class, new InstantDeserializer());
    module.addSerializer(byte[].class, new ByteArraySerializer());
    module.addDeserializer(byte[].class, new ByteArrayDeserializer());
    module.addSerializer(Buffer.class, new BufferSerializer());
    module.addDeserializer(Buffer.class, new BufferDeserializer());

    mapper.registerModule(module);
    prettyMapper.registerModule(module);
  }

  /**
   * @return the {@link ObjectMapper} used for data binding.
   */
  public static ObjectMapper mapper() {
    return mapper;
  }

  /**
   * @return the {@link ObjectMapper} used for data binding configured for indenting output.
   */
  public static ObjectMapper prettyMapper() {
    return prettyMapper;
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

  public static JsonParser createParser(Buffer buf) {
    try {
      return DatabindCodec.mapper.getFactory().createParser((InputStream) new ByteBufInputStream(buf.getByteBuf()));
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  public static JsonParser createParser(String str) {
    try {
      return DatabindCodec.mapper.getFactory().createParser(str);
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
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
      ObjectMapper mapper = pretty ? DatabindCodec.prettyMapper : DatabindCodec.mapper;
      return mapper.writeValueAsString(object);
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    try {
      ObjectMapper mapper = pretty ? DatabindCodec.prettyMapper : DatabindCodec.mapper;
      return Buffer.buffer(mapper.writeValueAsBytes(object));
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
