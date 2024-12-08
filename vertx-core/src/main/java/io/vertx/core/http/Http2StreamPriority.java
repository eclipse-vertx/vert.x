package io.vertx.core.http;

public class Http2StreamPriority extends StreamPriorityBase {
  private final StreamPriority streamPriority;

  public Http2StreamPriority(StreamPriority streamPriority) {
    this.streamPriority = streamPriority;
  }

  public Http2StreamPriority() {
    this(new StreamPriority());
  }

  public short getWeight() {
    return this.streamPriority.getWeight();
  }

  public Http2StreamPriority setWeight(short weight) {
    this.streamPriority.setWeight(weight);
    return this;
  }

  public int getDependency() {
    return this.streamPriority.getDependency();
  }

  public Http2StreamPriority setDependency(int dependency) {
    this.streamPriority.setDependency(dependency);
    return this;
  }

  public boolean isExclusive() {
    return this.streamPriority.isExclusive();
  }

  public Http2StreamPriority setExclusive(boolean exclusive) {
    this.streamPriority.setExclusive(exclusive);
    return this;
  }

  @Override
  public int urgency() {
    throw new RuntimeException("Not Http3 Priority!");
  }

  @Override
  public boolean isIncremental() {
    throw new RuntimeException("Not Http3 Priority!");
  }

  @Override
  public int hashCode() {
    return this.streamPriority.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Http2StreamPriority && this.streamPriority.equals(((Http2StreamPriority) obj).streamPriority);
  }

  @Override
  public StreamPriorityBase copy() {
    return new Http2StreamPriority()
      .setDependency(this.getDependency())
      .setExclusive(this.isExclusive())
      .setWeight(this.getWeight());
  }

  @Override
  public String toString() {
    return this.streamPriority.toString();
  }
}
