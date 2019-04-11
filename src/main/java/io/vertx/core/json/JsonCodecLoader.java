package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;

public class JsonCodecLoader {

  Map<Class, JsonCodec> jsonCodecMap;

  private JsonCodecLoader(Map<Class, JsonCodec> jsonCodecMap) {
    this.jsonCodecMap = jsonCodecMap;
  }

  public static JsonCodecLoader loadCodecsFromSPI() {
    Map<Class, JsonCodec> codecs = new HashMap<>();
    ServiceLoader<JsonCodec> codecServiceLoader = ServiceLoader.load(JsonCodec.class);
    for (JsonCodec j : codecServiceLoader) {
      codecs.put(j.getTargetClass(), j);
    }
    return new JsonCodecLoader(codecs);
  }

  @SuppressWarnings("unchecked")
  public <T> T decode(Buffer value, Class<T> c) {
    Object json = value.toJson();
    try {
      return (T) jsonCodecMap.get(c).decode(json);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to find codec for class " + c.getCanonicalName(), e);
    }
  }

  @SuppressWarnings("unchecked")
  public Buffer encode(Object value) {
    try {
      return Buffer.buffer(jsonCodecMap.get(value.getClass()).encode(value).toString());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to find codec for class " + value.getClass().getCanonicalName(), e);
    }
  }
}
