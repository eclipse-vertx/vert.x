package io.vertx.core.json.codecs;

import io.vertx.core.spi.json.JsonCodec;

import java.util.Objects;

public class MyShortPojo {

  public static class MyShortPojoJsonCodec implements JsonCodec<MyShortPojo, Number> {

    @Override
    public MyShortPojo decode(Number value) throws IllegalArgumentException {
      return new MyShortPojo().setValue(value.shortValue());
    }

    @Override
    public Short encode(MyShortPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyShortPojo> getTargetClass() {
      return MyShortPojo.class;
    }
  }

  short value;

  public short getValue() {
    return value;
  }

  public MyShortPojo setValue(short value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyShortPojo that = (MyShortPojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
