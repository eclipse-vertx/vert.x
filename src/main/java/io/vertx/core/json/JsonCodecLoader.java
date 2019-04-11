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
    try {
      return (T) retrieveCodec(c).decode(json);
    } catch (ClassCastException e) {
      // A codec accepts only a particular type, that could not be the same of json parameter.
      // This happens in particular with decodeBuffer(), when jackson decides what he wants for the serialized type
      // And it's even worse if someone configure the Jackson mapper to use always float or long or things like these
      // That's why these awful tricks
      if (json.getClass().equals(Double.class)) {
        return (T) retrieveCodec(c).decode(((Double)json).floatValue());
      }
      if (json.getClass().equals(Float.class)) {
        return (T) retrieveCodec(c).decode(((Float)json).doubleValue());
      }
      if (json.getClass().equals(String.class)) {
        return (T) retrieveCodec(c).decode(((String)json).charAt(0));
      }
      try {
        return decodeNaturalNumber((Number)json, c);
      } catch (ClassCastException e1) {
        throw e;
      }
    }
  }

  private <T> T decodeNaturalNumber(Number number, Class<T> c) {
    try {
      return (T) retrieveCodec(c).decode(number.intValue());
    } catch (ClassCastException e) {
      try {
        return (T) retrieveCodec(c).decode(number.longValue());
      } catch (ClassCastException e1) {
        return (T) retrieveCodec(c).decode(number.shortValue());
      }
    }
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
