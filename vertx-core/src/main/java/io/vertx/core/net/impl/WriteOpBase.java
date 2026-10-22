package io.vertx.core.net.impl;

public abstract class WriteOpBase implements WriteOp {

  private int depth;
  private int seq;
  private boolean writable;

  @Override
  public void cancel(Throwable cause) {
  }

  @Override
  public void init(int depth, int seq, boolean writable) {
    this.depth = depth;
    this.seq = seq;
    this.writable = writable;
  }

  @Override
  public int depth() {
    return depth;
  }

  @Override
  public int seq() {
    return seq;
  }

  @Override
  public void onDrain() {
  }

  @Override
  public void onDispose() {
  }
}
