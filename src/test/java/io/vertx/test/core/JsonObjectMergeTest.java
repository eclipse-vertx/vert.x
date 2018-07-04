package io.vertx.test.core;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="https://github.com/mystdeim">Roman Novikov</a>
 */
public class JsonObjectMergeTest {

  @Test
  public void testSuccessful() {
    Sample sample = new Sample();
    JsonObject json = new JsonObject().put("id", "1").put("name", "Sam");

    json.mergeIn(sample);
    assertEquals("1", sample.id);
    assertEquals("Sam", sample.name);
  }

  @Test(expected = RuntimeException.class)
  public void testFailed() {
    Sample sample = new Sample();
    JsonObject json = new JsonObject().put("id", "1").put("person", "Sam");

    json.mergeIn(sample);
  }

  /**
   * Sample model
   */
  class Sample {
    private String id;
    private String name;
    public Sample() {
    }
    public void setId(String id) {
      this.id = id;
    }
    public void setName(String name) {
      this.name = name;
    }
  }
}
