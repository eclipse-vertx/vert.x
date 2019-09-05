package io.vertx.core.json.mappers;

import io.vertx.core.spi.json.JsonMapper;

import java.util.Objects;

public class MyLongPojo {

  public static class MyLongPojoJsonMapper implements JsonMapper<MyLongPojo, Number> {

    @Override
    public MyLongPojo deserialize(Number value) throws IllegalArgumentException {
      return new MyLongPojo().setValue(value.longValue());
    }

    @Override
    public Long serialize(MyLongPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyLongPojo> getTargetClass() {
      return MyLongPojo.class;
    }
  }

  long value;

  public long getValue() {
    return value;
  }

  public MyLongPojo setValue(long value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyLongPojo that = (MyLongPojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
