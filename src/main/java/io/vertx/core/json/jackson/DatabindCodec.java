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

package io.vertx.core.json.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DatabindCodec extends JacksonCodec {

  private static final ObjectMapper mapper = new ObjectMapper(JacksonCodec.factory);

  static {
    initialize();
  }

  private static void initialize() {
    // Non-standard JSON but we allow C style comments in our JSON
    mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

    VertxModule module = new VertxModule();
    mapper.registerModule(module);
  }

  /**
   * @return the {@link ObjectMapper} used for data binding.
   */
  public static ObjectMapper mapper() {
    return mapper;
  }

  @Override
  public <T> T fromValue(Object json, Class<T> clazz) {
    return DatabindCodec.mapper.convertValue(json, clazz);
  }

  public <T> T fromValue(Object json, TypeReference<T> type) {
    return DatabindCodec.mapper.convertValue(json, type);
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
      return DatabindCodec.mapper.getFactory().createParser((InputStream) new ByteBufInputStream(((BufferInternal) buf).getByteBuf()));
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

  @Override
  public Object fromString(String str, boolean wrap) throws DecodeException {
    Object value = JacksonCodec.fromParser(createParser(str), Object.class);
    return wrap ? wrap(value) : value;
  }

  @Override
  public Object fromBuffer(Buffer buf, boolean wrap) throws DecodeException {
    Object value = JacksonCodec.fromParser(createParser(buf), Object.class);
    return wrap ? wrap(value) : value;
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
    return value;
  }

  private static <T> T fromParser(JsonParser parser, TypeReference<T> type) throws DecodeException {
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
}
