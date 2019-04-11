package io.vertx.core.json.codecs;

import io.vertx.core.json.JsonCodec;

import java.util.Objects;

public class MyCharPojo {

  public static class MyCharPojoJsonCodec implements JsonCodec<MyCharPojo, Character> {

    @Override
    public MyCharPojo decode(Character value) throws IllegalArgumentException {
      return new MyCharPojo().setValue(value);
    }

    @Override
    public Character encode(MyCharPojo value) throws IllegalArgumentException {
      return value.getValue();
    }

    @Override
    public Class<MyCharPojo> getTargetClass() {
      return MyCharPojo.class;
    }
  }

  char value;

  public char getValue() {
    return value;
  }

  public MyCharPojo setValue(char value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyCharPojo that = (MyCharPojo) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
