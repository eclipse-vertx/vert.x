package io.vertx.core.eventbus.impl;

import io.vertx.core.buffer.Buffer;

class FinFrame implements Frame {

  final String addr;

  public FinFrame(String addr) {
    this.addr = addr;
  }

  @Override
  public String address() {
    return addr;
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
