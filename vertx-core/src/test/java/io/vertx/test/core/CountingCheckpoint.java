package io.vertx.test.core;

import java.util.concurrent.CountDownLatch;

class CountingCheckpoint extends CountDownLatch {

  private final Checkpoint checkpoint;

  CountingCheckpoint(Checkpoint checkpoint, int count) {
    super(count);
    this.checkpoint = checkpoint;
  }

  @Override
  public void countDown() {
    super.countDown();
    if (getCount() == 0) {
      checkpoint.succeed();
    }
  }
}
