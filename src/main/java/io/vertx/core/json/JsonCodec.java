package io.vertx.core.json;

/**
 * This interface represents the Vert.x json representation of TARGET_TYPE. <br/>
 * The TARGET_TYPE could be any type, while JSON_TYPE could be one of the following:
 * <ul>
 *     <li>{@link JsonObject}</li>
 *     <li>{@link JsonArray}</li>
 *     <li>{@link String}</li>
 *     <li>{@link Number}</li>
 *     <li>{@link Boolean}</li>
 * </ul>
 *
 * To use it in {@code @ModuleGen} annotation you must provide a {@code public static final [JsonCodecType] INSTANCE} to let codegen retrieve an instance of it
 *
 * @param <TARGET_TYPE>
 * @param <JSON_TYPE>
 */
public interface JsonCodec <TARGET_TYPE, JSON_TYPE> extends JsonEncoder<TARGET_TYPE, JSON_TYPE>, JsonDecoder<TARGET_TYPE, JSON_TYPE> { }
