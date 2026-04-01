package io.vertx.test.core;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class TestResultTest {

  @Test
  public void testDefault() {
    try (var res = new TestResult()) {
    }
  }

  @Test
  public void testVerify() {
    try (var res = new TestResult()) {
      Completable<?> verifier = res.verifying();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        verifier.succeed();
      }).start();
    }
  }

  @Test
  public void testFail() {
    RuntimeException failure = new RuntimeException();
    try {
      try (var res = new TestResult()) {
        res.fail(failure);
      }
      throw new AssertionError();
    } catch (Exception e) {
      if (failure != e) {
        throw new AssertionError();
      }
    }
  }

  @Test
  public void testEval() {
    AtomicInteger count = new AtomicInteger();
    try (var res = new TestResult()) {
      res.eval(count::incrementAndGet);
    }
    if (count.get() != 1) {
      throw new AssertionError();
    }
  }

  @Test
  public void testEvalFailure() {
    RuntimeException failure = new RuntimeException();
    try {
      try (var res = new TestResult()) {
        res.eval(() -> {
          throw failure;
        });
      }
      throw new AssertionError();
    } catch (Exception e) {
      if (failure != e) {
        throw new AssertionError();
      }
    }
  }

  @Test
  public void testConcurrentClose() throws Exception {
    TestResult res = new TestResult();
    Completable<?> verifier = res.verifying();
    int num = 4;
    CyclicBarrier barrier = new CyclicBarrier(num);
    Thread[] runners = new Thread[num];
    Map<Thread, Throwable> caughts = new ConcurrentHashMap<>();
    Thread.UncaughtExceptionHandler handler = caughts::put;
    for (int i = 0;i < num;i++) {
      Thread runner = new Thread(() -> {
        try {
          barrier.await();
        } catch (InterruptedException | BrokenBarrierException ignore) {
        }
        res.close();
      });
      runner.setUncaughtExceptionHandler(handler);
      runners[i] = runner;
      runner.start();
    }
    while (caughts.size() != num - 1) {
      Thread.sleep(10);
    }
    verifier.succeed();
    for (int i = 0;i < num;i++) {
      runners[i].join();
    }
  }

  @Test
  public void testVariance() {
    try (var res = new TestResult()) {
      Completable<Object> verifier = res.verifying();
      Future<Void> succeeded = Future.succeededFuture();
      succeeded.onComplete(verifier);
    }
  }
}
