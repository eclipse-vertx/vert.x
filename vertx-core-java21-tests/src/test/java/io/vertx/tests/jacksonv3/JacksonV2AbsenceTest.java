package io.vertx.tests.jacksonv3;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.Json;

public class JacksonV2AbsenceTest {

  @Test
  public void testJacksonVersion() {
    try {
      JsonObject.class.getClassLoader().loadClass("com.fasterxml.jackson.core.JsonFactory");
      fail();
    } catch (ClassNotFoundException expected) {
    }
  }

  @Test
  public void testFactory() {
    assertEquals("io.vertx.core.json.jackson.v3.DatabindCodec", Json.CODEC.getClass().getName());
  }

  @Test
  public void testJsonObject() {
    JsonObject json = new JsonObject("{\"key\":\"value\"}");
    assertEquals("value", json.getString("key"));
    assertEquals("{\"key\":\"value\"}", json.toString());
  }
}
