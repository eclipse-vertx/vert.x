package io.vertx.it.jacksonv3;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.Json;

public class JacksonV2PresenceTest {

  @Test
  public void testJacksonVersion() throws Exception {
    JsonObject.class.getClassLoader().loadClass("com.fasterxml.jackson.core.JsonFactory");
    JsonObject.class.getClassLoader().loadClass("tools.jackson.core.json.JsonFactory");
  }

  @Test
  public void testFactory() {
    assertEquals("io.vertx.core.json.jackson.JacksonCodec", Json.CODEC.getClass().getName());
  }

  @Test
  public void testJsonObject() {
    JsonObject json = new JsonObject("{\"key\":\"value\"}");
    assertEquals("value", json.getString("key"));
    assertEquals("{\"key\":\"value\"}", json.toString());
  }
}
