package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.json.JsonCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class JsonCodecMapper implements JsonMapper {

  private final Map<Class, JsonCodec> codecMap;

  JsonCodecMapper() {
    this.codecMap = new HashMap<>();
    ServiceLoader<JsonCodec> codecServiceLoader = ServiceLoader.load(JsonCodec.class);
    for (JsonCodec j : codecServiceLoader) {
      this.codecMap.put(j.getTargetClass(), j);
    }
  }

  private <T> JsonCodec codec(Class<T> c) {
    return codecMap.get(c);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T decode(Object json, Class<T> c) {
    if (json == null) {
      return null;
    }
    JsonCodec<T, Object> codec = (JsonCodec<T, Object>) codecMap.get(c);
    if (codec == null) {
      throw new IllegalStateException("Unable to find codec for class " + c.getName());
    }
    try {
      return codec.decode(json);
    } catch (Exception e) {
      throw new DecodeException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object encode(Object value) {
    if (value == null) {
      return null;
    }
    JsonCodec<Object, Object> codec = (JsonCodec<Object, Object>) codecMap.get(value.getClass());
    if (codec == null) {
      throw new IllegalStateException("Unable to find codec for class " + value.getClass().getName());
    }
    try {
      return codec.encode(value);
    } catch (Exception e) {
      throw new EncodeException(e);
    }
  }
}
