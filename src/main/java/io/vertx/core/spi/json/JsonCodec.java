package io.vertx.core.spi.json;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converts {@code <T>} back and forth to {@code <J>}.
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
 * To use it in {@code @ModuleGen} annotation you must provide
 * a {@code public static final [JsonCodecType] INSTANCE}.
 *
 * @param <T> the type encoded to Json
 * @param <J> the json type
 */
public interface JsonCodec<T, J> extends JsonEncoder<T, J>, JsonDecoder<T, J> {
}
