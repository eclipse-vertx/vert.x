package io.vertx.core.impl;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Alexander Schwartz
 */
public class TaskQueueTest {

  Executor executorThatAlwaysThrowsRejectedExceptions = new Executor() {
    @Override
    public void execute(Runnable command) {
      throw new RejectedExecutionException();
    }
  };

  TaskQueue taskQueue = new TaskQueue();

  @Test
  public void shouldNotHaveTaskInQueueWhenTaskHasBeenRejected() {
    assertThatThrownBy(
      () -> taskQueue.execute(new Thread(), executorThatAlwaysThrowsRejectedExceptions)
    ).isInstanceOf(RejectedExecutionException.class);

    Assertions.assertThat(taskQueue.isEmpty()).isTrue();
  }

}
