package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;

/**
 * NOTE: This class couldn't be an interface because it is being used in the
 * {@link io.vertx.core.http.HttpClientRequest}
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

}
