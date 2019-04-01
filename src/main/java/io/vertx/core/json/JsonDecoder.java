package io.vertx.core.json;

/**
 * Primitive for conversion JSON_TYPE -> TARGET_TYPE
 *
 * @param <TARGET_TYPE>
 * @param <JSON_TYPE>
 */
public interface JsonDecoder<TARGET_TYPE, JSON_TYPE> {
  /**
   * decode performs the conversion JSON_TYPE -> TARGET_TYPE <br/>
   * It expects value not null and must not return a null value
   *
   * @param value
   * @return
   * @throws IllegalArgumentException when it cannot decode the value
   */
  TARGET_TYPE decode(JSON_TYPE value) throws IllegalArgumentException;
}
