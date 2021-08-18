package io.vertx.core.impl.utils;

import java.util.function.Supplier;

public class ConstantSupplier<T> implements Supplier<T> {

  final T value;

  public ConstantSupplier(T value) {
    this.value = value;
  }

  @Override
  public T get() {
    return value;
  }
}
