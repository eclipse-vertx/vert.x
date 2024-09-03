package io.vertx.core.http;

public interface StreamPriorityBase {
  short getWeight();

  StreamPriorityBase setWeight(short weight);

  int getDependency();

  StreamPriorityBase setDependency(int dependency);

  boolean isExclusive();

  StreamPriorityBase setExclusive(boolean exclusive);

  int urgency();

  boolean isIncremental();

}
