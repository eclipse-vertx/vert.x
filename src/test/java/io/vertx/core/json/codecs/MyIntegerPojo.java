package io.vertx.core.json.codecs;

import io.vertx.core.spi.json.JsonCodec;

import java.util.Objects;

public class MyIntegerPojo {

  public static class MyIntegerPojoJsonCodec implements JsonCodec<MyIntegerPojo, Number> {

    @Override
    public MyIntegerPojo decode(Number value) throws IllegalArgumentException {
      return new MyIntegerPojo().setValue(value.intValue());
    }

    @Override
    public Integer encode(MyIntegerPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyIntegerPojo> getTargetClass() {
      return MyIntegerPojo.class;
    }
  }

  int value;

  public int getValue() {
    return value;
  }

  public MyIntegerPojo setValue(int value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyIntegerPojo that = (MyIntegerPojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
