package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * NOTE: This class cannot be an interface because it is used in {@link io.vertx.core.http.HttpClientRequest}
 */
@DataObject
@JsonGen(publicConverter = false)
public class StreamPriorityBase {
  public short getWeight() {
    throw new RuntimeException("Not implemented in child class");
  }

  public StreamPriorityBase setWeight(short weight) {
    throw new RuntimeException("Not implemented in child class");
  }

  public int getDependency() {
    throw new RuntimeException("Not implemented in child class");
  }

  public StreamPriorityBase setDependency(int dependency) {
    throw new RuntimeException("Not implemented in child class");
  }

  public boolean isExclusive() {
    throw new RuntimeException("Not implemented in child class");
  }

  public StreamPriorityBase setExclusive(boolean exclusive) {
    throw new RuntimeException("Not implemented in child class");
  }

  public int urgency() {
    throw new RuntimeException("Not implemented in child class");
  }

  public boolean isIncremental() {
    throw new RuntimeException("Not implemented in child class");
  }

  public StreamPriorityBase() {
  }

  public StreamPriorityBase(JsonObject json) {
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    StreamPriorityBaseConverter.toJson(this, json);
    return json;
  }
}
