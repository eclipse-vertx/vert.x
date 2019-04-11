package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

@SuppressWarnings("unchecked")
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

  private <T> JsonCodec retrieveCodec(Class<T> c) {
    JsonCodec codec = jsonCodecMap.get(c);
    if (codec == null) throw new IllegalStateException("Unable to find codec for class " + c.getName());
    return codec;
  }

  public <T> T decode(Object json, Class<T> c) {
    return (T) retrieveCodec(c).decode(json);
  }

  public <T> T decodeBuffer(Buffer value, Class<T> c) {
    return decode(Json.decodeValue(value), c);
  }

  public Object encode(Object value) {
    return retrieveCodec(value.getClass()).encode(value);
  }

  public Buffer encodeBuffer(Object value) {
    return Json.encodeToBuffer(encode(value));
  }
}
