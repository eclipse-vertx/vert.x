package io.vertx.core.json.codecs;

import io.vertx.core.spi.json.JsonCodec;

import java.util.Objects;

public class MyDoublePojo {

  public static class MyDoublePojoJsonCodec implements JsonCodec<MyDoublePojo, Number> {

    @Override
    public MyDoublePojo decode(Number value) throws IllegalArgumentException {
      return new MyDoublePojo().setValue(value.doubleValue());
    }

    @Override
    public Double encode(MyDoublePojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyDoublePojo> getTargetClass() {
      return MyDoublePojo.class;
    }
  }

  double value;

  public double getValue() {
    return value;
  }

  public MyDoublePojo setValue(double value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyDoublePojo that = (MyDoublePojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
