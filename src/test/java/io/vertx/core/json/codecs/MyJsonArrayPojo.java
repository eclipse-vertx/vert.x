package io.vertx.core.json.codecs;

import io.vertx.core.spi.json.JsonCodec;
import io.vertx.core.json.JsonArray;

import java.util.Objects;

public class MyJsonArrayPojo {

  public static class MyJsonArrayPojoJsonCodec implements JsonCodec<MyJsonArrayPojo, JsonArray> {

    @Override
    public MyJsonArrayPojo decode(JsonArray value) throws IllegalArgumentException {
      return new MyJsonArrayPojo().setValue(value);
    }

    @Override
    public JsonArray encode(MyJsonArrayPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyJsonArrayPojo> getTargetClass() {
      return MyJsonArrayPojo.class;
    }
  }

  JsonArray value;

  public JsonArray getValue() {
    return value;
  }

  public MyJsonArrayPojo setValue(JsonArray value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyJsonArrayPojo that = (MyJsonArrayPojo) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
