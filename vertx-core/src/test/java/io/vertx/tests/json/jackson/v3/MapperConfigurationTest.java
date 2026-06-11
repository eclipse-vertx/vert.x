package io.vertx.tests.json.jackson.v3;

import io.vertx.core.json.jackson.v3.DatabindCodec;
import org.junit.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertThrows;

public class MapperConfigurationTest {

  public static class User {
    private int age;

    public int getAge() {
      return age;
    }

    public void setAge(int age) {
      this.age = age;
    }
  }

  @Test
  public void testConfigurationChanges() {
    final ObjectMapper oldMapper = DatabindCodec.mapper();
    assertThrows(JacksonException.class, () -> oldMapper.readValue("{\"age\": null}", User.class));

    DatabindCodec.updateMapper(builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build());

    final ObjectMapper newMapper = DatabindCodec.mapper();
    newMapper.readValue("{\"age\": null}", User.class);
  }
}
