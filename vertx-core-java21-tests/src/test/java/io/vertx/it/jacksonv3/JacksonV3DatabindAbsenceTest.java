package io.vertx.it.jacksonv3;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.Json;

public class JacksonV3DatabindAbsenceTest {

  @Test
  public void testJacksonVersion() throws Exception {
    try {
      JsonObject.class.getClassLoader().loadClass("com.fasterxml.jackson.core.JsonFactory");
      fail();
    } catch (ClassNotFoundException expected) {
    }
    try {
      JsonObject.class.getClassLoader().loadClass("tools.jackson.databind.ObjectMapper");
      fail();
    } catch (ClassNotFoundException expected) {
    }
    JsonObject.class.getClassLoader().loadClass("tools.jackson.core.json.JsonFactory");
  }

  @Test
  public void testFactory() {
    assertEquals("io.vertx.core.json.jackson.v3.JacksonCodec", Json.CODEC.getClass().getName());
  }

  @Test
  public void testJsonObject() {
    JsonObject json = new JsonObject("{\"key\":\"value\"}");
    assertEquals("value", json.getString("key"));
    assertEquals("{\"key\":\"value\"}", json.toString());
  }
}
