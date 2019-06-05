package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.json.JsonCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

class JsonCodecMapper {

  private static final Map<Class, JsonCodec> codecMap;

  static {
    Map<Class, JsonCodec> map = new HashMap<>();
    ServiceLoader<JsonCodec> codecServiceLoader = ServiceLoader.load(JsonCodec.class);
    for (JsonCodec j : codecServiceLoader) {
      map.put(j.getTargetClass(), j);
    }
    codecMap = map;
  }

  private static <T> JsonCodec codec(Class<T> c) {
    return codecMap.get(c);
  }

  public static <T> T decode(Object json, Class<T> c) {
    if (json == null) {
      return null;
    }
    JsonCodec<T, Object> codec = (JsonCodec<T, Object>) codecMap.get(c);
    if (codec == null) {
      throw new IllegalStateException("Unable to find codec for class " + c.getName());
    }
    return codec.decode(json);
  }

  public static <T> T decodeBuffer(Buffer value, Class<T> c) {
    return decode(Json.decodeValue(value), c);
  }

  public static Object encode(Object value) {
    if (value == null) {
      return null;
    }
    JsonCodec<Object, Object> codec = (JsonCodec<Object, Object>) codecMap.get(value.getClass());
    if (codec == null) {
      throw new IllegalStateException("Unable to find codec for class " + value.getClass().getName());
    }
    return codec.encode(value);
  }

  public static Buffer encodeBuffer(Object value) {
    return Json.encodeToBuffer(encode(value));
  }
}
