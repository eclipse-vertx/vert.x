package io.vertx.core.jacksonv3;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.json.JsonCodec;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.JsonTokenId;
import tools.jackson.core.io.SegmentedStringWriter;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.BufferRecycler;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.vertx.core.json.impl.JsonUtil.BASE64_DECODER;
import static io.vertx.core.json.impl.JsonUtil.BASE64_ENCODER;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class JacksonCodec implements JsonCodec {

  private static final JsonFactory factory = new JsonFactory();

  public JacksonCodec() {
  }

  private static JsonParser createParser(String str) {
    return factory.createParser(str);
  }

  private static JsonGenerator createGenerator(Writer out, boolean pretty) {
    JsonGenerator generator = factory.createGenerator(out);
    if (pretty) {
      throw new UnsupportedOperationException();
    }
    return generator;
  }

  @Override
  public <T> T fromString(String json, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(json), clazz);
  }

  @Override
  public <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T fromValue(Object json, Class<T> toValueType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    BufferRecycler br = factory._getBufferRecycler();
    try (SegmentedStringWriter sw = new SegmentedStringWriter(br)) {
      JsonGenerator generator = createGenerator(sw, pretty);
      encodeJson(object, generator);
      generator.close();
      return sw.getAndClear();
    } catch (Exception e) {
      throw new EncodeException(e.getMessage(), e);
    } finally {
      br.releaseToPool();
    }
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    throw new UnsupportedOperationException();
  }

  public static <T> T fromParser(JsonParser parser, Class<T> type) throws DecodeException {
    Object res;
    JsonToken remaining;
    try {
      parser.nextToken();
      res = parseAny(parser);
      remaining = parser.nextToken();
    } catch (IOException e) {
      throw new DecodeException(e.getMessage(), e);
    } finally {
      close(parser);
    }
    if (remaining != null) {
      throw new DecodeException("Unexpected trailing token");
    }
    return cast(res, type);
  }

  private static Object parseAny(JsonParser parser) throws IOException, DecodeException {
    switch (parser.currentTokenId()) {
      case JsonTokenId.ID_START_OBJECT:
        return parseObject(parser);
      case JsonTokenId.ID_START_ARRAY:
        return parseArray(parser);
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
        throw new DecodeException("Unexpected token"/*, parser.getCurrentLocation()*/);
    }
  }

  private static Map<String, Object> parseObject(JsonParser parser) throws IOException {
    String key1 = parser.nextName();
    if (key1 == null) {
      return new LinkedHashMap<>(2);
    }
    parser.nextToken();
    Object value1 = parseAny(parser);
    String key2 = parser.nextName();
    if (key2 == null) {
      LinkedHashMap<String, Object> obj = new LinkedHashMap<>(2);
      obj.put(key1, value1);
      return obj;
    }
    parser.nextToken();
    Object value2 = parseAny(parser);
    String key = parser.nextName();
    if (key == null) {
      LinkedHashMap<String, Object> obj = new LinkedHashMap<>(2);
      obj.put(key1, value1);
      obj.put(key2, value2);
      return obj;
    }
    // General case
    LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
    obj.put(key1, value1);
    obj.put(key2, value2);
    do {
      parser.nextToken();
      Object value = parseAny(parser);
      obj.put(key, value);
      key = parser.nextName();
    } while (key != null);
    return obj;
  }

  private static List<Object> parseArray(JsonParser parser) throws IOException {
    List<Object> array = new ArrayList<>();
    while (true) {
      parser.nextToken();
      int tokenId = parser.currentTokenId();
      if (tokenId == JsonTokenId.ID_PROPERTY_NAME) {
        throw new UnsupportedOperationException();
      } else if (tokenId == JsonTokenId.ID_END_ARRAY) {
        return array;
      }
      Object value = parseAny(parser);
      array.add(value);
    }
  }

  static void close(Closeable parser) {
    try {
      parser.close();
    } catch (IOException ignore) {
    }
  }

  // In recursive calls, the callee is in charge of opening and closing the data structure
  public static void encodeJson(Object json, JsonGenerator generator) throws EncodeException {
    try {
      if (json instanceof JsonObject) {
        json = ((JsonObject)json).getMap();
      } else if (json instanceof JsonArray) {
        json = ((JsonArray)json).getList();
      }
      if (json instanceof Map) {
        generator.writeStartObject();
        for (Map.Entry<String, ?> e : ((Map<String, ?>)json).entrySet()) {
          generator.writeName(e.getKey());
          Object value = e.getValue();
          encodeJson0(value, generator);
        }
        generator.writeEndObject();
      } else if (json instanceof List) {
        generator.writeStartArray();
        for (Object item : (List<?>) json) {
          encodeJson0(item, generator);
        }
        generator.writeEndArray();
      } else if (!encodeSingleType(generator, json)) {
        throw new EncodeException("Mapping " + json.getClass().getName() + "  is not available without Jackson Databind on the classpath");
      }
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }

  /**
   * This is a way to overcome a limit of OpenJDK on MaxRecursiveInlineLevel:
   * avoiding the "direct" recursive calls allow the JIT to have a better inlining budget for the recursive calls.
   */
  private static void encodeJson0(Object json, JsonGenerator generator) throws EncodeException {
    try {
      if (json instanceof JsonObject) {
        json = ((JsonObject)json).getMap();
      } else if (json instanceof JsonArray) {
        json = ((JsonArray)json).getList();
      }
      if (json instanceof Map) {
        generator.writeStartObject();
        for (Map.Entry<String, ?> e : ((Map<String, ?>)json).entrySet()) {
          generator.writeName(e.getKey());
          Object value = e.getValue();
          encodeJson(value, generator);
        }
        generator.writeEndObject();
      } else if (json instanceof List) {
        generator.writeStartArray();
        for (Object item : (List<?>) json) {
          encodeJson(item, generator);
        }
        generator.writeEndArray();
      } else if (!encodeSingleType(generator, json)) {
        throw new EncodeException("Mapping " + json.getClass().getName() + "  is not available without Jackson Databind on the classpath");
      }
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }

  private static boolean encodeSingleType(JsonGenerator generator, Object json) throws IOException {
    if (json == null) {
      generator.writeNull();
    } else if (json instanceof String) {
      generator.writeString((String) json);
    } else if (json instanceof Number) {
      encodeNumber(generator, json);
    } else if (json instanceof Boolean) {
      generator.writeBoolean((Boolean)json);
    } else if (json instanceof Instant) {
      // RFC-7493
      generator.writeString((ISO_INSTANT.format((Instant)json)));
    } else if (json instanceof byte[]) {
      // RFC-7493
      generator.writeString(BASE64_ENCODER.encodeToString((byte[]) json));
    } else if (json instanceof Buffer) {
      // RFC-7493
      generator.writeString(BASE64_ENCODER.encodeToString(((Buffer) json).getBytes()));
    } else if (json instanceof Enum) {
      // vert.x extra (non standard but allowed conversion)
      generator.writeString(((Enum<?>) json).name());
    } else {
      return false;
    }
    return true;
  }

  private static void encodeNumber(JsonGenerator generator, Object json) throws IOException {
    if (json instanceof Short) {
      generator.writeNumber((Short) json);
    } else if (json instanceof Integer) {
      generator.writeNumber((Integer) json);
    } else if (json instanceof Long) {
      generator.writeNumber((Long) json);
    } else if (json instanceof Float) {
      generator.writeNumber((Float) json);
    } else if (json instanceof Double) {
      generator.writeNumber((Double) json);
    } else if (json instanceof Byte) {
      generator.writeNumber((Byte) json);
    } else if (json instanceof BigInteger) {
      generator.writeNumber((BigInteger) json);
    } else if (json instanceof BigDecimal) {
      generator.writeNumber((BigDecimal) json);
    } else {
      generator.writeNumber(((Number) json).doubleValue());
    }
  }

  private static <T> T cast(Object o, Class<T> clazz) {
    if (o instanceof Map) {
      if (!clazz.isAssignableFrom(Map.class)) {
        throw new DecodeException("Failed to decode");
      }
      if (clazz == Object.class) {
        o = new JsonObject((Map) o);
      }
      return clazz.cast(o);
    } else if (o instanceof List) {
      if (!clazz.isAssignableFrom(List.class)) {
        throw new DecodeException("Failed to decode");
      }
      if (clazz == Object.class) {
        o = new JsonArray((List) o);
      }
      return clazz.cast(o);
    } else if (o instanceof String) {
      String str = (String) o;
      if (clazz.isEnum()) {
        o = Enum.valueOf((Class<Enum>) clazz, str);
      } else if (clazz == byte[].class) {
        o = BASE64_DECODER.decode(str);
      } else if (clazz == Buffer.class) {
        o = Buffer.buffer(BASE64_DECODER.decode(str));
      } else if (clazz == Instant.class) {
        o = Instant.from(ISO_INSTANT.parse(str));
      } else if (!clazz.isAssignableFrom(String.class)) {
        throw new DecodeException("Failed to decode");
      }
      return clazz.cast(o);
    } else if (o instanceof Boolean) {
      if (!clazz.isAssignableFrom(Boolean.class)) {
        throw new DecodeException("Failed to decode");
      }
      return clazz.cast(o);
    } else if (o == null) {
      return null;
    } else {
      Number number = (Number) o;
      if (clazz == Integer.class) {
        o = number.intValue();
      } else if (clazz == Long.class) {
        o = number.longValue();
      } else if (clazz == Float.class) {
        o = number.floatValue();
      } else if (clazz == Double.class) {
        o = number.doubleValue();
      } else if (clazz == Byte.class) {
        o = number.byteValue();
      } else if (clazz == Short.class) {
        o = number.shortValue();
      } else if (clazz == Object.class || clazz.isAssignableFrom(Number.class)) {
        // Nothing
      } else {
        throw new DecodeException("Failed to decode");
      }
      return clazz.cast(o);
    }
  }
}
