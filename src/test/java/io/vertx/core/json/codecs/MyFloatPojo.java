package io.vertx.core.json.codecs;

import io.vertx.core.json.JsonCodec;

import java.util.Objects;

public class MyFloatPojo {

  public static class MyFloatPojoJsonCodec implements JsonCodec<MyFloatPojo, Float> {

    @Override
    public MyFloatPojo decode(Float value) throws IllegalArgumentException {
      return new MyFloatPojo().setValue(value);
    }

    @Override
    public Float encode(MyFloatPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyFloatPojo> getTargetClass() {
      return MyFloatPojo.class;
    }
  }

  float value;

  public float getValue() {
    return value;
  }

  public MyFloatPojo setValue(float value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyFloatPojo that = (MyFloatPojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
