package io.vertx.core.json.codecs;

import io.vertx.core.spi.json.JsonMapper;

import java.util.Objects;

public class MyShortPojo {

  public static class MyShortPojoJsonCodec implements JsonMapper<MyShortPojo, Number> {

    @Override
    public MyShortPojo deserialize(Number value) throws IllegalArgumentException {
      return new MyShortPojo().setValue(value.shortValue());
    }

    @Override
    public Short serialize(MyShortPojo value) throws IllegalArgumentException {
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
