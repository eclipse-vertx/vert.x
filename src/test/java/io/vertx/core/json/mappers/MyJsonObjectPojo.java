package io.vertx.core.json.mappers;

import io.vertx.core.spi.json.JsonMapper;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

public class MyJsonObjectPojo {

  public static class MyJsonObjectPojoJsonMapper implements JsonMapper<MyJsonObjectPojo, JsonObject> {

    @Override
    public MyJsonObjectPojo deserialize(JsonObject value) throws IllegalArgumentException {
      return new MyJsonObjectPojo().setValue(value);
    }

    @Override
    public JsonObject serialize(MyJsonObjectPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyJsonObjectPojo> getTargetClass() {
      return MyJsonObjectPojo.class;
    }
  }

  JsonObject value;

  public JsonObject getValue() {
    return value;
  }

  public MyJsonObjectPojo setValue(JsonObject value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyJsonObjectPojo that = (MyJsonObjectPojo) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
