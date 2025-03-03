package io.vertx.tests.context;

import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.test.core.AsyncTestBase;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TaskQueueTest extends AsyncTestBase {

  private TaskQueue taskQueue;
  private Executor executor;
  private List<Thread> threads = Collections.synchronizedList(new ArrayList<>());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    taskQueue = new TaskQueue();
    AtomicInteger idx = new AtomicInteger();
    executor = cmd -> {
      new Thread(cmd, "vert.x-" + idx.getAndIncrement()).start();
    };
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      for (int i = 0;i < threads.size();i++) {
        threads.get(i).join();
      }
    } finally {
      threads.clear();
    }
    super.tearDown();
  }

  private void suspendAndAwaitResume(CountDownLatch suspend) {
    try {
      suspend.await();
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  @Test
  public void testCreateThread() throws Exception {
    AtomicReference<Thread> thread = new AtomicReference<>();
    taskQueue.execute(() -> {
      thread.set(Thread.currentThread());
    }, executor);
    waitUntil(() -> thread.get() != null);
    Thread.sleep(10);
    taskQueue.execute(() -> {
      assertNotSame(thread.get(), Thread.currentThread());
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testAwaitSchedulesOnNewThread() {
    taskQueue.execute(() -> {
      Thread current = Thread.currentThread();
      taskQueue.execute(() -> {
        assertNotSame(current, Thread.currentThread());
        testComplete();
      }, executor);
      CountDownLatch suspend = taskQueue.current().trySuspend();
      suspendAndAwaitResume(suspend);
    }, executor);
    await();
  }

  @Test
  public void testResumeFromAnotherThread() {
    taskQueue.execute(() -> {
      WorkerExecutor.Execution execution = taskQueue.current();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        execution.resume();
      }).start();
      CountDownLatch suspend = execution.trySuspend();
      suspendAndAwaitResume(suspend);
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testResumeFromContextThread() {
    taskQueue.execute(() -> {
      WorkerExecutor.Execution execution = taskQueue.current();
      CountDownLatch suspend = execution.trySuspend();
      taskQueue.execute(() -> {
        // Make sure the awaiting thread will block on the internal future before resolving it (could use thread status)
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        execution.resume();
      }, executor);
      suspendAndAwaitResume(suspend);
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testResumeWhenIdle() {
    taskQueue.execute(() -> {
      AtomicReference<Thread> ref = new AtomicReference<>();
      WorkerExecutor.Execution execution = taskQueue.current();
      new Thread(() -> {
        Thread th;
        while ((th = ref.get()) == null) {
          try {
            Thread.sleep(1);
          } catch (InterruptedException ignore) {
          }
        }
        try {
          th.join(2_000);
        } catch (InterruptedException ignore) {
          ignore.printStackTrace(System.out);
        }
        execution.resume();
      }).start();
      CountDownLatch cond = execution.trySuspend();
      taskQueue.execute(() -> ref.set(Thread.currentThread()), executor);
      suspendAndAwaitResume(cond);
      testComplete();
    }, executor);
    await();
  }

  // Need to do unschedule when nested test!

  @Test
  public void testUnscheduleRace2() {
    AtomicInteger seq = new AtomicInteger();
    taskQueue.execute(() -> {
      assertEquals("vert.x-0", Thread.currentThread().getName());
      CompletableFuture<Void> cf = new CompletableFuture<>();
      taskQueue.execute(() -> {
        assertEquals("vert.x-0", Thread.currentThread().getName());
        assertEquals(0, seq.getAndIncrement());
        WorkerExecutor.Execution execution = taskQueue.current();
        cf.whenComplete((v, e) -> execution.resume(() -> {
          assertEquals("vert.x-1", Thread.currentThread().getName());
          assertEquals(2, seq.getAndIncrement());
        }));
        suspendAndAwaitResume(execution.trySuspend());
        assertEquals(3, seq.getAndIncrement());
      }, executor);
      AtomicBoolean enqueued = new AtomicBoolean();
      taskQueue.execute(() -> {
        assertEquals("vert.x-1", Thread.currentThread().getName());
        assertEquals(1, seq.getAndIncrement());
        while (!enqueued.get()) {
          // Wait until next task is enqueued
        }
        cf.complete(null);
      }, executor);
      taskQueue.execute(() -> {
        assertEquals("vert.x-0", Thread.currentThread().getName());
        assertEquals(4, seq.getAndIncrement());
        testComplete();
      }, executor);
      enqueued.set(true);
    }, executor);

    await();
  }

  @Test
  public void shouldNotHaveTaskInQueueWhenTaskHasBeenRejected() {
    Executor executorThatAlwaysThrowsRejectedExceptions = command -> {
      throw new RejectedExecutionException();
    };
    TaskQueue taskQueue = new TaskQueue();
    assertThatThrownBy(
      () -> taskQueue.execute(this::fail, executorThatAlwaysThrowsRejectedExceptions)
    ).isInstanceOf(RejectedExecutionException.class);

    Assertions.assertThat(taskQueue.isEmpty()).isTrue();
  }

  @Test
  public void testCloseSuspendedTasks() {
    TaskQueue taskQueue = new TaskQueue();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor executor = pending::add;
    Runnable task = () -> {
      CountDownLatch latch = taskQueue.current().trySuspend();
    };
    taskQueue.execute(task, executor);
    assertEquals(1, pending.size());
    pending.pop().run();
    TaskQueue.CloseResult result = taskQueue.close();
    assertEquals(1, result.suspendedTasks().size());
    assertEquals(1, result.suspendedThreads().size());
    assertSame(task, result.suspendedTasks().get(0));
  }

  @Test
  public void testCloseResumingTasks() {
    TaskQueue taskQueue = new TaskQueue();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor executor = pending::add;
    AtomicReference<WorkerExecutor.Execution> ref = new AtomicReference<>();
    Runnable task = () -> {
      WorkerExecutor.Execution t = taskQueue.current();
      ref.set(t);
      t.trySuspend();
    };
    taskQueue.execute(task, executor);
    assertEquals(1, pending.size());
    taskQueue.execute(() -> {}, command -> {
      // Use different executor to queue resume
    });
    pending.pop().run();
    ref.get().resume();
    TaskQueue.CloseResult result = taskQueue.close();
    assertEquals(1, result.suspendedTasks().size());
    assertEquals(1, result.suspendedThreads().size());
    assertSame(task, result.suspendedTasks().get(0));
  }

  @Test
  public void testCloseBeforeSuspend() {
    TaskQueue taskQueue = new TaskQueue();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor exec = pending::add;
    AtomicReference<TaskQueue.CloseResult> result = new AtomicReference<>();
    taskQueue.execute(() -> {
      Thread th = new Thread(() -> {
        result.set(taskQueue.close());
      });
      th.start();
      try {
        th.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      WorkerExecutor.Execution execution = taskQueue.current();
      CountDownLatch cont = execution.trySuspend();
      assertNull(cont);
    }, exec);
    Runnable t = pending.pop();
    t.run();
    assertTrue(result.get().suspendedThreads().isEmpty());
    assertNotNull(result.get().activeThread());
  }

  @Test
  public void testCloseBeforeResumeExecution() {
    TaskQueue taskQueue = new TaskQueue();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor exec = pending::add;
    taskQueue.execute(() -> {
      WorkerExecutor.Execution execution = taskQueue.current();
      execution.resume();
      assertNull(execution.trySuspend());
      taskQueue.close();
    }, exec);
    Runnable t = pending.pop();
    t.run();
    assertEquals(0, pending.size());
  }

  @Test
  public void testCloseBetweenSuspendAndAwait() {
    TaskQueue taskQueue = new TaskQueue();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor exec = pending::add;
    AtomicBoolean interrupted = new AtomicBoolean();
    taskQueue.execute(() -> {
      WorkerExecutor.Execution execution = taskQueue.current();
      CountDownLatch latch = execution.trySuspend();
      AtomicBoolean closed = new AtomicBoolean();
      Thread th = new Thread(() -> {
        TaskQueue.CloseResult res = taskQueue.close();
        res.suspendedThreads().get(0).interrupt();
        closed.set(true);
      });
      th.start();
      while (!closed.get()) {
        Thread.yield();
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        interrupted.set(true);
      }
    }, exec);
    Runnable t = pending.pop();
    t.run();
    assertTrue(interrupted.get());
  }

  @Test
  public void testSubmitAfterClose() {
    TaskQueue taskQueue = new TaskQueue();
    taskQueue.close();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor exec = pending::add;
    taskQueue.execute(() -> {

    }, exec);
    assertEquals(1, pending.size());
  }

  @Test
  public void testSuspendAfterResume() {
    AtomicInteger seq = new AtomicInteger();
    TaskQueue taskQueue = new TaskQueue();
    Deque<Runnable> pending = new ConcurrentLinkedDeque<>();
    Executor exec = pending::add;
    taskQueue.execute(() -> {
      assertEquals(0, seq.getAndIncrement());
      taskQueue.execute(() -> {
        assertEquals(2, seq.getAndIncrement());
      }, executor);
      WorkerExecutor.Execution execution = taskQueue.current();
      assertEquals(1, seq.getAndIncrement());
      execution.resume();
      CountDownLatch latch = execution.trySuspend();
      assertNull(latch);
    }, exec);
    pending.poll().run();
    assertEquals(2, seq.get());
  }
}
