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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="https://slinkydeveloper.com">slinkydeveloper</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Json {

  public static final JsonFactory factory = new JsonFactory();
  public static final JsonMapper mapper = load();

  static {
    // Non-standard JSON but we allow C style comments in our JSON
    factory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
  }

  /**
   * Encode a POJO or a Vert.x json data structure to a String containing the JSON representation
   *
   * @param value a valid Vert.x json ({@link JsonObject}, {@link JsonArray} or primitive) or a POJO.
   * @return a String containing the JSON representation of the given json.
   * @throws EncodeException when the json cannot be encoded
   */
  public static String encode(Object value) throws EncodeException {
    try {
      Object json = mapFrom(value);
      StringWriter sw = new StringWriter();
      JsonGenerator generator = factory.createGenerator(sw);
      encodeJson(json, generator);
      generator.flush();
      return sw.toString();
    } catch (IOException e) {
      throw new EncodeException(e);
    }
  }

  /**
   * Encode a POJO or a Vert.x json data structure to a {@link Buffer} containing the JSON representation
   *
   * @param value a valid Vert.x json ({@link JsonObject}, {@link JsonArray} or primitive) or a POJO.
   * @return a Buffer containing the JSON representation of the given json.
   * @throws EncodeException when the json cannot be encoded
   */
  public static Buffer encodeToBuffer(Object value) throws EncodeException {
    return Buffer.buffer(encode(value));
  }

  /**
   * Encode a POJO or a Vert.x json data structure to JSON with pretty indentation
   *
   * @param value a valid Vert.x json ({@link JsonObject}, {@link JsonArray} or primitive) or a POJO.
   * @return a String containing the JSON representation of the given json.
   * @throws EncodeException when the json cannot be encoded
   */
  public static String encodePrettily(Object value) throws EncodeException {
    try {
      Object json = mapFrom(value);
      StringWriter sw = new StringWriter();
      JsonGenerator generator = factory.createGenerator(sw);
      generator.useDefaultPrettyPrinter();
      encodeJson(json, generator);
      generator.flush();
      return sw.toString();
    } catch (IOException e) {
      throw new EncodeException(e);
    }
  }

  /**
   * Convert a POJO to a Vert.x json data structure.
   * <p>
   * This method tries to use a matching {@link io.vertx.core.spi.json.JsonCodec} for the provided {@code pojo}.
   * If no {@link io.vertx.core.spi.json.JsonCodec} is found, it fallbacks to jackson-databind if you have provided it as project dependency,
   * otherwise it throws a {@link DecodeException}
   *
   * @param pojo the pojo to convert
   * @return a valid Vert.x json ({@link JsonObject}, {@link JsonArray} or primitive).
   * @throws EncodeException If there was an error during encoding or the internal mapper can't encode the provided pojo
   */
  public static Object mapFrom(Object pojo) throws EncodeException {
    try {
      if (pojo instanceof JsonObject || pojo instanceof JsonArray || pojo instanceof Number || pojo instanceof Boolean || pojo instanceof String || pojo == null)
        return pojo;
      else
        return mapper.encode(pojo);
    } catch (IllegalStateException e) {
      throw new EncodeException(e);
    }
  }

  /**
   * Decode a given JSON string to a Vert.x Json data structure.
   *
   * @param str the JSON string.
   *
   * @return a valid Vert.x json ({@link JsonObject}, {@link JsonArray} or primitive).
   * @throws DecodeException when the json cannot be decoded
   */
  public static Object decodeValue(String str) throws DecodeException {
    return wrapIfNecessary(decodeValueInternal(str));
  }

  /**
   * Decode a given JSON buffer to a Vert.x Json data structure.
   *
   * @param buf the JSON buffer.
   *
   * @return a valid Vert.x json ({@link JsonObject}, {@link JsonArray} or primitive).
   * @throws DecodeException when the json cannot be decoded.
   */
  public static Object decodeValue(Buffer buf) throws DecodeException {
    return wrapIfNecessary(decodeValueInternal(buf));
  }

  /**
   * Convert a Vert.x json data structure to a POJO.
   * <p>
   * This method tries to use a matching {@link io.vertx.core.spi.json.JsonCodec} for the provided {@code clazz}.
   * If no {@link io.vertx.core.spi.json.JsonCodec} is found, it fallbacks to jackson-databind if you have provided it as project dependency,
   * otherwise it throws an {@link DecodeException}
   *
   * @param json the json data structure.
   * @param clazz the class to map to.
   * @return an instance of the class to map to.
   * @throws DecodeException If there was an error during decoding or the internal mapper can't decode the provided pojo.
   */
  public static <T> T mapTo(Object json, Class<T> clazz) throws DecodeException {
    try {
      return mapper.decode(json, clazz);
    } catch (IllegalStateException e) {
      throw new DecodeException(e);
    }
  }

  /**
   * Decode a given JSON string to a POJO of the given class type.
   *
   * @param str the JSON string.
   * @param clazz the class to map to.
   * @param <T> the generic type.
   * @return an instance of T.
   * @throws DecodeException If there was an error during decoding or the internal mapper can't decode the provided pojo.
   */
  public static <T> T decodeValue(String str, Class<T> clazz) throws DecodeException {
    return mapTo(decodeValue(str), clazz);
  }

  /**
   * Decode a given JSON string to a POJO of the given class type.
   * <p>
   * You need {@code jackson-databind} in your classpath
   *
   * @param str the JSON string.
   * @param type the type to map to.
   * @param <T> the generic type.
   * @return an instance of T.
   * @throws DecodeException If there was an error during decoding or the internal mapper can't decode the provided pojo.
   */
  public static <T> T decodeValue(String str, TypeReference<T> type) throws DecodeException {
    try {
      return mapper.decode(decodeValue(str), type);
    } catch (IllegalStateException e) {
      throw new DecodeException(e);
    }
  }

  /**
   * Decode a given JSON buffer to a POJO of the given class type.
   *
   * @param buf the JSON buffer.
   * @param clazz the class to map to.
   * @param <T> the generic type.
   * @return an instance of T.
   * @throws DecodeException If there was an error during decoding or the internal mapper can't decode the provided pojo.
   */
  public static <T> T decodeValue(Buffer buf, Class<T> clazz) throws DecodeException {
    return decodeValue(buf.toString(), clazz);
  }

  /**
   * Decode a given JSON buffer to a POJO of the given class type.
   *
   * @param buf the JSON buffer.
   * @param type the type to map to.
   * @param <T> the generic type.
   * @return an instance of T.
   * @throws DecodeException If there was an error during decoding or the internal mapper can't decode the provided pojo.
   */
  public static <T> T decodeValue(Buffer buf, TypeReference<T> type) throws DecodeException {
    return decodeValue(buf.toString(), type);
  }

  @SuppressWarnings("unchecked")
  static Object checkAndCopy(Object val, boolean copy) {
    if (val == null) {
      // OK
    } else if (val instanceof Number && !(val instanceof BigDecimal)) {
      // OK
    } else if (val instanceof Boolean) {
      // OK
    } else if (val instanceof String) {
      // OK
    } else if (val instanceof Character) {
      // OK
    } else if (val instanceof CharSequence) {
      val = val.toString();
    } else if (val instanceof JsonObject) {
      if (copy) {
        val = ((JsonObject) val).copy();
      }
    } else if (val instanceof JsonArray) {
      if (copy) {
        val = ((JsonArray) val).copy();
      }
    } else if (val instanceof Map) {
      if (copy) {
        val = (new JsonObject((Map)val)).copy();
      } else {
        val = new JsonObject((Map)val);
      }
    } else if (val instanceof List) {
      if (copy) {
        val = (new JsonArray((List)val)).copy();
      } else {
        val = new JsonArray((List)val);
      }
    } else if (val instanceof byte[]) {
      val = Base64.getEncoder().encodeToString((byte[])val);
    } else if (val instanceof Instant) {
      val = ISO_INSTANT.format((Instant) val);
    } else {
      throw new IllegalStateException("Illegal type in JsonObject: " + val.getClass());
    }
    return val;
  }

  static <T> Stream<T> asStream(Iterator<T> sourceIterator) {
    Iterable<T> iterable = () -> sourceIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  private static Object wrapIfNecessary(Object o) {
    if (o instanceof Map) {
      o = new JsonObject((Map<String, Object>) o);
    } else if (o instanceof List) {
      o = new JsonArray((List) o);
    }
    return o;
  }

  static Object decodeValueInternal(String str) throws DecodeException {
    try {
      JsonParser parser = factory.createParser(str);
      parser.nextToken();
      return decodeJsonInternal(parser);
    } catch (IOException e) {
      throw new DecodeException(e);
    }
  }

  static Object decodeValueInternal(Buffer buf) throws DecodeException {
    try {
      JsonParser parser = factory.createParser(buf.getBytes());
      parser.nextToken();
      return decodeJsonInternal(parser);
    } catch (IOException e) {
      throw new DecodeException(e);
    }
  }

  private static Object decodeJsonInternal(JsonParser parser) throws DecodeException {
    try {
      // Check if root object is a primitive or not
      switch (parser.getCurrentTokenId()) {
        case JsonTokenId.ID_START_OBJECT:
          Map<String, Object> object = new LinkedHashMap<>();
          decodeObject(parser, object);
          return object;
        case JsonTokenId.ID_START_ARRAY:
          List<Object> array = new ArrayList<>();
          decodeArray(parser, array);
          return array;
        case JsonTokenId.ID_STRING:
          return parser.getText();
        case JsonTokenId.ID_NUMBER_FLOAT:
        case JsonTokenId.ID_NUMBER_INT:
          return parser.getNumberValue();
        case JsonTokenId.ID_TRUE:
          return Boolean.TRUE;
        case JsonTokenId.ID_FALSE:
          return Boolean.FALSE;
        case JsonTokenId.ID_NULL:
          return null;
        default:
          throw DecodeException.create("Unexpected token", parser.getCurrentLocation());
      }
    } catch (IOException e) {
      throw new DecodeException(e);
    }
  }

  private static void decodeObject(JsonParser parser, Map<String, Object> current) throws DecodeException {
    try {
      while (true) {
        JsonToken token = parser.nextToken();
        if (token != JsonToken.FIELD_NAME) {
          break;
        }
        String name = parser.getText();
        parser.nextToken();
        Object value = decodeJsonInternal(parser);
        current.put(name, value);
      }
    } catch (IOException e) {
      throw new DecodeException(e);
    }
  }

  private static void decodeArray(JsonParser parser, List<Object> current) throws DecodeException {
    try {
      while (true) {
        parser.nextToken();
        int tokenId = parser.getCurrentTokenId();
        if (tokenId == JsonTokenId.ID_FIELD_NAME) {
          throw new UnsupportedOperationException();
        } else if (tokenId == JsonTokenId.ID_END_ARRAY) {
          return;
        }
        Object value = decodeJsonInternal(parser);
        current.add(value);
      }
    } catch (IOException e) {
      throw new DecodeException(e);
    }
  }

  // In recursive calls, the callee is in charge of opening and closing the data structure
  static void encodeJson(Object json, JsonGenerator generator) throws EncodeException {
    try {
      if (json instanceof JsonObject) {
        generator.writeStartObject();
        for (Map.Entry<String, Object> e : (JsonObject)json) {
          generator.writeFieldName(e.getKey());
          encodeJson(e.getValue(), generator);
        }
        generator.writeEndObject();
      }
      if (json instanceof JsonArray) {
        generator.writeStartArray();
        for (Object item : (JsonArray)json) {
          encodeJson(item, generator);
        }
        generator.writeEndArray();
      }
      if (json instanceof String) {
        generator.writeString((String)json);
      }
      if (json instanceof Number) {
        if (json instanceof Short) {
          generator.writeNumber((Short) json);
        }
        if (json instanceof Integer) {
          generator.writeNumber((Integer) json);
        }
        if (json instanceof Long) {
          generator.writeNumber((Long) json);
        }
        if (json instanceof Float) {
          generator.writeNumber((Float) json);
        }
        if (json instanceof Double) {
          generator.writeNumber((Double) json);
        }
      }
      if (json instanceof Boolean) {
        generator.writeBoolean((Boolean)json);
      }
      if (json == null) {
        generator.writeNull();
      }
    } catch (IOException e) {
      throw new EncodeException(e);
    }
  }

  private static JsonMapper load() {
    try {
      return new JacksonMapper();
    } catch (Throwable t1) {
      return new JsonCodecMapper();
    }
  }
}
