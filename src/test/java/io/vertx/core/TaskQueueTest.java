package io.vertx.core;

import io.vertx.core.impl.TaskQueue;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    CountDownLatch latch = new CountDownLatch(1);
    taskQueue.execute(() -> {
      Thread current = Thread.currentThread();
      taskQueue.execute(() -> {
        assertNotSame(current, Thread.currentThread());
        testComplete();
      }, executor);
      taskQueue.unschedule();
      try {
        latch.await();
      } catch (InterruptedException ignore) {
      }
    }, executor);
    await();
  }

  @Test
  public void testResumeFromAnotherThread() {
    taskQueue.execute(() -> {
      CountDownLatch latch = new CountDownLatch(1);
      Consumer<Runnable> detach = taskQueue.unschedule();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        detach.accept(() -> {
          latch.countDown();
        });
        try {
          latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          fail(e);
        }
      }).start();
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testResumeFromContextThread() {
    taskQueue.execute(() -> {
      CountDownLatch latch = new CountDownLatch(1);
      Consumer<Runnable> detach = taskQueue.unschedule();
      taskQueue.execute(() -> {
        // Make sure the awaiting thread will block on the internal future before resolving it (could use thread status)
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        detach.accept(() -> {
          latch.countDown();
        });
      }, executor);
      try {
        latch.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        fail(e);
      }
      testComplete();
    }, executor);
    await();
  }

  @Test
  public void testResumeWhenIdle() {
    taskQueue.execute(() -> {
      CountDownLatch latch = new CountDownLatch(1);
      Consumer<Runnable> l = taskQueue.unschedule();
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
        l.accept(latch::countDown);
      }).start();
      taskQueue.execute(() -> ref.set(Thread.currentThread()), executor);
      try {
        latch.await();
      } catch (InterruptedException ignore) {
      }
      testComplete();
    }, executor);
    await();
  }
}
