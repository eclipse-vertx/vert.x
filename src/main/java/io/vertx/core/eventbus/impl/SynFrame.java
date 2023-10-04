package io.vertx.core.eventbus.impl;

import io.vertx.core.buffer.Buffer;

public class SynFrame implements Frame {

  final String src;
  final String dst;

  public SynFrame(String src, String dst) {
    this.src = src;
    this.dst = dst;
  }

  @Override
  public String address() {
    return dst;
  }

  @Override
  public Buffer encodeToWire() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFromWire() {
    return false;
  }
}
