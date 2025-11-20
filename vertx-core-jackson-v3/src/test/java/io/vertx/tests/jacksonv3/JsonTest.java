package io.vertx.tests.jacksonv3;

import io.vertx.core.jacksonv3.JacksonCodec;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JsonTest {

  @Test
  public void testNoJacksonV2() {
    try {
      JsonTest.class.getClassLoader().loadClass("com.fasterxml.jackson.core.JsonFactory");
      fail();
    } catch (ClassNotFoundException expected) {
    }
  }

  @Test
  public void testFactory() {
    assertEquals(JacksonCodec.class, Json.CODEC.getClass());
  }

  @Test
  public void testSome() {
    JsonObject json = new JsonObject("{\"key\":\"value\"}");
    assertEquals("value", json.getString("key"));
    assertEquals("{\"key\":\"value\"}", json.toString());
  }
}
