package io.vertx.core.json;

import com.fasterxml.jackson.core.type.TypeReference;

public interface JsonMapper {

  /**
   * Encode a POJO to a Json data structure
   *
   * @param value
   * @return
   * @throws EncodeException If there was an error during encoding
   * @throws IllegalStateException If this mapper instance can't map the provided POJO
   */
  Object encode(Object value) throws EncodeException, IllegalStateException;

  /**
   * Decode a POJO from a Json data structure
   *
   * @param json
   * @param c
   * @return
   * @throws DecodeException If there was an error during decoding
   * @throws IllegalStateException If this mapper instance can't handle the provided class
   */
  <T> T decode(Object json, Class<T> c) throws DecodeException, IllegalStateException;

  <T> T decode(Object json, TypeReference<T> t) throws DecodeException, IllegalStateException;

  static JsonMapper load() {
    try {
      return new JacksonMapper();
    } catch (Throwable t1) {
      return new JsonCodecMapper();
    }
  }

}
