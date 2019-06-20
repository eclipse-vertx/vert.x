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
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="https://slinkydeveloper.com">slinkydeveloper</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Json {

  public static ObjectMapper mapper = new ObjectMapper();
  public static ObjectMapper prettyMapper = new ObjectMapper();

  private static final JsonFactory factory = new JsonFactory();

  static {
    // Non-standard JSON but we allow C style comments in our JSON
    factory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

    SimpleModule module = new SimpleModule();
    // custom types
    module.addSerializer(JsonObject.class, new JsonObjectSerializer());
    module.addSerializer(JsonArray.class, new JsonArraySerializer());
    // he have 2 extensions: RFC-7493
    module.addSerializer(Instant.class, new InstantSerializer());
    module.addDeserializer(Instant.class, new InstantDeserializer());
    module.addSerializer(byte[].class, new ByteArraySerializer());
    module.addDeserializer(byte[].class, new ByteArrayDeserializer());

    mapper.registerModule(module);
    prettyMapper.registerModule(module);
  }

  /**
   * Encode a POJO to JSON using the underlying Jackson mapper.
   *
   * @param json a POJO
   * @return a String containing the JSON representation of the given POJO.
   * @throws EncodeException if a property cannot be encoded.
   */
  public static String encode(Object json) throws EncodeException {
    try {
      StringWriter sw = new StringWriter();
      JsonGenerator generator = factory.createGenerator(sw);
      encodeJson(json, generator);
      generator.flush();
      return sw.toString();
    } catch (IOException e) {
      throw EncodeException.create(e);
    }
  }

  /**
   * Encode a POJO to JSON using the underlying Jackson mapper.
   *
   * @param json a POJO
   * @return a Buffer containing the JSON representation of the given POJO.
   * @throws EncodeException if a property cannot be encoded.
   */
  public static Buffer encodeToBuffer(Object json) throws EncodeException {
    return Buffer.buffer(encode(json));
  }

  /**
   * Encode a POJO to JSON with pretty indentation, using the underlying Jackson mapper.
   *
   * @param json a POJO
   * @return a String containing the JSON representation of the given POJO.
   * @throws EncodeException if a property cannot be encoded.
   */
  public static String encodePrettily(Object json) throws EncodeException {
    try {
      StringWriter sw = new StringWriter();
      JsonGenerator generator = factory.createGenerator(sw);
      generator.useDefaultPrettyPrinter();
      encodeJson(json, generator);
      generator.flush();
      return sw.toString();
    } catch (IOException e) {
      throw EncodeException.create(e);
    }
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
    try {
      return mapper.readValue(str, clazz);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode: " + e.getMessage());
    }
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
    try {
      JsonParser parser = factory.createParser(str);
      parser.nextToken();
      return decodeJson(parser);
    } catch (IOException e) {
      throw DecodeException.create(e);
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
  //TODO?
  public static <T> T decodeValue(String str, TypeReference<T> type) throws DecodeException {
    try {
      return mapper.readValue(str, type);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode: " + e.getMessage(), e);
    }
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
    return decodeValue(buf.toString());
  }

  /**
   * Decode a given JSON buffer to a POJO of the given class type.
   * @param buf the JSON buffer.
   * @param type the type to map to.
   * @param <T> the generic type.
   * @return an instance of T
   * @throws DecodeException when there is a parsing or invalid mapping.
   */
  //TODO?
  public static <T> T decodeValue(Buffer buf, TypeReference<T> type) throws DecodeException {
    try {
      return mapper.readValue(new ByteBufInputStream(buf.getByteBuf()), type);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
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
    try {
      return mapper.readValue((InputStream) new ByteBufInputStream(buf.getByteBuf()), clazz);
    } catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
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

  // In recursive calls, the callee is in charge of calling parser.nextToken()
  static Object decodeJson(JsonParser parser) throws DecodeException {
    try {
      // JsonObject
      if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
        JsonObject jo = new JsonObject();
        parser.nextToken();
        while (parser.getCurrentToken() != JsonToken.END_OBJECT) {
          if (parser.getCurrentToken() != JsonToken.FIELD_NAME) {
            throw DecodeException.create("Expecting field name", parser.getCurrentLocation());
          }
          String fieldName = parser.getCurrentName();
          parser.nextToken();

          Object fieldValue = decodeJson(parser);
          jo.put(fieldName, fieldValue);
        }
        parser.nextToken();
        return jo;
      }
      // JsonArray
      if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
        JsonArray ja = new JsonArray();
        parser.nextToken();
        while (parser.getCurrentToken() != JsonToken.END_ARRAY) {
          Object item = decodeJson(parser);
          ja.add(item);
        }
        parser.nextToken();
        return ja;
      }
      // String
      if (parser.getCurrentToken() == JsonToken.VALUE_STRING) {
        String val = parser.getText();
        parser.nextToken();
        return val;
      }
      // Numbers
      if (parser.getCurrentToken().isNumeric()) {
        Number val = parser.getNumberValue();
        parser.nextToken();
        return val;
      }
      // Booleans
      if (parser.getCurrentToken().isBoolean()) {
        Boolean val = parser.getBooleanValue();
        parser.nextToken();
        return val;
      }
      // Null
      if (parser.getCurrentToken() == JsonToken.VALUE_NULL) {
        parser.nextToken();
        return null;
      }

      throw DecodeException.create("Unexpected token", parser.getCurrentLocation());
    } catch (IOException e) {
      throw DecodeException.create(e);
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
      throw EncodeException.create(e);
    }
  }

  private static class JsonObjectSerializer extends JsonSerializer<JsonObject> {
    @Override
    public void serialize(JsonObject value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      jgen.writeObject(value.getMap());
    }
  }

  private static class JsonArraySerializer extends JsonSerializer<JsonArray> {
    @Override
    public void serialize(JsonArray value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      jgen.writeObject(value.getList());
    }
  }

  private static class InstantSerializer extends JsonSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      jgen.writeString(ISO_INSTANT.format(value));
    }
  }

  private static class InstantDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      String text = p.getText();
      try {
        return Instant.from(ISO_INSTANT.parse(text));
      } catch (DateTimeException e) {
        throw new InvalidFormatException(p, "Expected an ISO 8601 formatted date time", text, Instant.class);
      }
    }
  }

  private static class ByteArraySerializer extends JsonSerializer<byte[]> {

    @Override
    public void serialize(byte[] value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      jgen.writeString(Base64.getEncoder().encodeToString(value));
    }
  }

  private static class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      String text = p.getText();
      try {
        return Base64.getDecoder().decode(text);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(p, "Expected a base64 encoded byte array", text, Instant.class);
      }
    }
  }
}
