package io.vertx.core.eventbus.impl.codecs;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.util.function.Function;

/**
 * @author <a href="http://slinkydeveloper.com">Francesco Guardiani</a>
 */
public class LocalShareableCodec<T> implements MessageCodec<T, T> {

  private final String name;
  private final Function<T, T> copyFunction;

  public LocalShareableCodec(String name, Function<T, T> copyFunction) {
    this.name = name;
    this.copyFunction = copyFunction;
  }

  @Override
  public void encodeToWire(Buffer buffer, T o) {
    throw new UnsupportedOperationException("This class doesn't support encoding to the wire");
  }

  @Override
  public T decodeFromWire(int pos, Buffer buffer) {
    throw new UnsupportedOperationException("This class doesn't support decoding from the wire");
  }

  @Override
  public T transform(T o) {
    return this.copyFunction.apply(o);
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public byte systemCodecID() {
    return -1;
  }
}
