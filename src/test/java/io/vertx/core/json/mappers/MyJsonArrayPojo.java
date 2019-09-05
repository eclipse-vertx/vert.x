package io.vertx.core.json.mappers;

import io.vertx.core.spi.json.JsonMapper;
import io.vertx.core.json.JsonArray;

import java.util.Objects;

public class MyJsonArrayPojo {

  public static class MyJsonArrayPojoJsonMapper implements JsonMapper<MyJsonArrayPojo, JsonArray> {

    @Override
    public MyJsonArrayPojo deserialize(JsonArray value) throws IllegalArgumentException {
      return new MyJsonArrayPojo().setValue(value);
    }

    @Override
    public JsonArray serialize(MyJsonArrayPojo value) throws IllegalArgumentException {
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
