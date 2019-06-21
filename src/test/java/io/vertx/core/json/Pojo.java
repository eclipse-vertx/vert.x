package io.vertx.core.json;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.spi.json.JsonCodec;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@DataObject
public class Pojo {

  public static class PojoJsonCodec implements JsonCodec<Pojo, JsonObject> {

    public static final PojoJsonCodec INSTANCE = new PojoJsonCodec();

    @Override
    public Pojo decode(JsonObject json) throws IllegalArgumentException {
      return new Pojo(json);
    }

    @Override
    public JsonObject encode(Pojo value) throws IllegalArgumentException {
      return value.toJson();
    }

    @Override
    public Class<Pojo> getTargetClass() {
      return Pojo.class;
    }
  }

  String value;
  Instant instant;
  byte[] bytes;

  public Pojo() {}

  public Pojo(JsonObject object) {
    if (object.containsKey("value")) this.value = object.getString("value");
    if (object.containsKey("instant")) this.instant = object.getInstant("instant");
    if (object.containsKey("bytes")) this.bytes = object.getBinary("bytes");
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (value != null) json.put("value", value);
    if (instant != null) json.put("instant", instant);
    if (bytes != null) json.put("bytes", bytes);
    return json;
  }

  public String getValue() {
    return value;
  }

  @Fluent
  public Pojo setValue(String value) {
    this.value = value;
    return this;
  }

  public Instant getInstant() {
    return instant;
  }

  @Fluent
  public Pojo setInstant(Instant instant) {
    this.instant = instant;
    return this;
  }

  public byte[] getBytes() {
    return bytes;
  }

  @Fluent
  public Pojo setBytes(byte[] bytes) {
    this.bytes = bytes;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Pojo pojo = (Pojo) o;
    return Objects.equals(value, pojo.value) &&
      Objects.equals(instant, pojo.instant) &&
      Arrays.equals(bytes, pojo.bytes);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(value, instant);
    result = 31 * result + Arrays.hashCode(bytes);
    return result;
  }
}
