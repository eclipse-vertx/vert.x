package io.vertx.core.spi.json;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converts {@code <T>} type to {@code <J>}.
 * <br/>
 * {@code <T>} can be any class or interface type, {@code <J>} can be one of the following:
 * <ul>
 *     <li>{@link JsonObject}</li>
 *     <li>{@link JsonArray}</li>
 *     <li>{@link String}</li>
 *     <li>{@link Number}</li>
 *     <li>{@link Boolean}</li>
 * </ul>
 *
 * @param <T> the type decoded from Json
 * @param <J> the json type
 */
public interface JsonDecoder<T, J> {

  /**
   * Decode {@code <J>} to {@code <T>}.
   * <br/>
   * The {@code json} will not be {@code null} and the implementation must not return {@code null}.
   *
   * @param json the json value
   * @return the decoded value
   * @throws IllegalArgumentException when it cannot decode the value
   */
  T decode(J json) throws IllegalArgumentException;

  /**
   * @return the class for {@code <T>}
   */
  Class<T> getTargetClass();
}
