package io.vertx.core.json;

/**
 * This interface represents the Vert.x json representation of any <TARGET_TYPE> <br/>
 *
 * To use it in {@code @ModuleGen} annotation you must provide a {@code public static final [JsonCodecType] INSTANCE} to let codegen retrieve an instance of it
 *
 * @param <TARGET_TYPE>
 * @param <JSON_TYPE>
 */
public interface JsonCodec <TARGET_TYPE, JSON_TYPE> extends JsonEncoder<TARGET_TYPE, JSON_TYPE>, JsonDecoder<TARGET_TYPE, JSON_TYPE> { }
