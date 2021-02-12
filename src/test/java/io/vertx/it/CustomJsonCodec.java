package io.vertx.it;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.spi.json.JsonCodec;

public class CustomJsonCodec implements JsonCodec {

  @Override
  public <T> T fromString(String json, Class<T> clazz) throws DecodeException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T fromValue(Object json, Class<T> toValueType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    return new StringBuilder().append(object).reverse().toString();
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    return Buffer.buffer(toString(object, pretty));
  }
}
