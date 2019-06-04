package io.vertx.core.json.codecs;

import io.vertx.core.spi.json.JsonCodec;

import java.util.Objects;

public class MyBooleanPojo {

  public static class MyBooleanPojoJsonCodec implements JsonCodec<MyBooleanPojo, Boolean> {

    @Override
    public MyBooleanPojo decode(Boolean value) throws IllegalArgumentException {
      return new MyBooleanPojo().setValue(value);
    }

    @Override
    public Boolean encode(MyBooleanPojo value) throws IllegalArgumentException {
      return value.isValue();
    }

    @Override
    public Class<MyBooleanPojo> getTargetClass() {
      return MyBooleanPojo.class;
    }
  }

  boolean value;

  public boolean isValue() {
    return value;
  }

  public MyBooleanPojo setValue(boolean value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyBooleanPojo that = (MyBooleanPojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
