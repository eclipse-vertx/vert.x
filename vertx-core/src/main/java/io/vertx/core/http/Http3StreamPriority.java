package io.vertx.core.http;

import io.netty.incubator.codec.quic.QuicStreamPriority;

public class Http3StreamPriority implements StreamPriorityBase {
  private final QuicStreamPriority quicStreamPriority;

  public Http3StreamPriority(QuicStreamPriority quicStreamPriority) {
    this.quicStreamPriority = quicStreamPriority;
  }

  public int urgency() {
    return this.quicStreamPriority.urgency();
  }

  public boolean isIncremental() {
    return this.quicStreamPriority.isIncremental();
  }

  @Override
  public short getWeight() {
    throw new RuntimeException("Not Http2 Priority!");
  }

  @Override
  public StreamPriorityBase setWeight(short weight) {
    throw new RuntimeException("Not Http2 Priority!");
  }

  @Override
  public int getDependency() {
    throw new RuntimeException("Not Http2 Priority!");
  }

  @Override
  public StreamPriorityBase setDependency(int dependency) {
    throw new RuntimeException("Not Http2 Priority!");
  }

  @Override
  public boolean isExclusive() {
    throw new RuntimeException("Not Http2 Priority!");
  }

  @Override
  public StreamPriorityBase setExclusive(boolean exclusive) {
    throw new RuntimeException("Not Http2 Priority!");
  }

  @Override
  public int hashCode() {
    return this.quicStreamPriority.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Http3StreamPriority && this.quicStreamPriority.equals(((Http3StreamPriority) obj).quicStreamPriority);
  }

  @Override
  public String toString() {
    return this.quicStreamPriority.toString();
  }
}
