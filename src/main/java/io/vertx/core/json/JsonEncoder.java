package io.vertx.core.json;

/**
 * Primitive for conversion TARGET_TYPE -> JSON_TYPE<br/>
 * The TARGET_TYPE could be any type, while JSON_TYPE could be one of the following:
 * <ul>
 *     <li>{@link JsonObject}</li>
 *     <li>{@link JsonArray}</li>
 *     <li>{@link String}</li>
 *     <li>{@link Number}</li>
 *     <li>{@link Boolean}</li>
 * </ul>
 *
 * @param <TARGET_TYPE>
 * @param <JSON_TYPE>
 */
public interface JsonEncoder<TARGET_TYPE, JSON_TYPE> {
  /**
   * encode performs the conversion TARGET_TYPE -> JSON_TYPE <br/>
   * It expects value not null and must not return a null value
   *
   * @param value
   * @return
   * @throws IllegalArgumentException when it cannot encode the value
   */
  JSON_TYPE encode(TARGET_TYPE value) throws IllegalArgumentException;

  Class<TARGET_TYPE> getTargetClass();
}
