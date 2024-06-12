package io.vertx.core;

import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

  private void suspendAndAwaitResume(WorkerExecutor.TaskController controller) {
    try {
      controller.suspendAndAwaitResume();
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
      WorkerExecutor.TaskController cont = taskQueue.current();
      suspendAndAwaitResume(cont);
    }, executor);
    await();
  }

  @Test
  public void testResumeFromAnotherThread() {
    taskQueue.execute(() -> {
      WorkerExecutor.TaskController continuation = taskQueue.current();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        continuation.resume();
        suspendAndAwaitResume(continuation);
      }).start();
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testResumeFromContextThread() {
    taskQueue.execute(() -> {
      WorkerExecutor.TaskController continuation = taskQueue.current();
      taskQueue.execute(() -> {
        // Make sure the awaiting thread will block on the internal future before resolving it (could use thread status)
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        continuation.resume();
      }, executor);
      suspendAndAwaitResume(continuation);
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testResumeWhenIdle() {
    taskQueue.execute(() -> {
      WorkerExecutor.TaskController cont = taskQueue.current();
      AtomicReference<Thread> ref = new AtomicReference<>();
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
        cont.resume();
      }).start();
      taskQueue.execute(() -> ref.set(Thread.currentThread()), executor);
      suspendAndAwaitResume(cont);
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testRaceResumeBeforeSuspend() {
    AtomicInteger seq = new AtomicInteger();
    taskQueue.execute(() -> {
      taskQueue.execute(() -> {
        WorkerExecutor.TaskController cont = taskQueue.current();
        cont.resume(() -> {
          assertEquals(1, seq.getAndIncrement());
        });
        assertEquals(0, seq.getAndIncrement());
        suspendAndAwaitResume(cont);
        assertEquals(2, seq.getAndIncrement());
      }, executor);
      taskQueue.execute(() -> {
        assertEquals(3, seq.getAndIncrement());
        testComplete();
      }, executor);
    }, executor);
    await();
  }

  // Need to do unschedule when nested test!

  @Test
  public void testUnscheduleRace2() {
    AtomicInteger seq = new AtomicInteger();
    taskQueue.execute(() -> {
      CompletableFuture<Void> cf = new CompletableFuture<>();
      taskQueue.execute(() -> {
        assertEquals("vert.x-0", Thread.currentThread().getName());
        assertEquals(0, seq.getAndIncrement());
        WorkerExecutor.TaskController cont = taskQueue.current();
        cf.whenComplete((v, e) -> cont.resume(() -> {
          assertEquals("vert.x-1", Thread.currentThread().getName());
          assertEquals(2, seq.getAndIncrement());
        }));
        suspendAndAwaitResume(cont);
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
        assertEquals(3, seq.getAndIncrement());
        testComplete();
      }, executor);
      enqueued.set(true);
    }, executor);

    await();
  }
}
