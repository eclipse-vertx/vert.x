package io.vertx.core.spi.json;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converts {@code <J>} type to {@code <T>}.
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
 * @param <T> the type encoded to Json
 * @param <J> the json type
 */
public interface JsonEncoder<T, J> {

  /**
   * Encode {@code <T>} to {@code <J>}.
   * <br/>
   * The {@code value} will not be {@code null} and the implementation must not return {@code null}.
   *
   * @param value the value
   * @return the encoded json value
   * @throws IllegalArgumentException when it cannot decode the value
   */
  J encode(T value) throws IllegalArgumentException;

  /**
   * @return the class for {@code <T>}
   */
  Class<T> getTargetClass();
}
