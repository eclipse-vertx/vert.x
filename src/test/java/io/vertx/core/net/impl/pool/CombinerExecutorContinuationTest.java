package io.vertx.core.net.impl.pool;

import io.vertx.test.core.AsyncTestBase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static java.lang.Boolean.TRUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

public class CombinerExecutorContinuationTest extends AsyncTestBase {

  @Rule
  public ErrorCollector errors = new ErrorCollector() {
    @Override
    public synchronized void addError(Throwable error) {
      super.addError(error);
    }

    @Override
    protected synchronized void verify() throws Throwable {
      super.verify();
    }
  };

  @FunctionalInterface
  private interface ExRunnable {

    void run() throws Exception;
  }

  private static Callable<Boolean> asCallable(ExRunnable runnable) {
    return () -> {
      runnable.run();
      return TRUE;
    };
  }

  @Test
  public void yieldWithMaxCountDeadlineControlExecutionDuration() throws InterruptedException, ExecutionException {
    CombinerExecutor<Object> sync = new CombinerExecutor<>(new Object(), CombinerExecutor.YieldCondition.withMaxCount(1));
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    final int TEST_ITERATION = 10000;
    try {
      for (int t = 0; t < TEST_ITERATION; t++) {
        final CountDownLatch submissionsWithoutContinuation = new CountDownLatch(1);
        final CountDownLatch winnerExecuting = new CountDownLatch(1);
        CompletableFuture<Executor.Continuation> continuationResult = new CompletableFuture<>();
        executorService.execute(() -> {
          AtomicReference<Thread> executed = new AtomicReference<>();
          Executor.Continuation continuation = sync.submitAndContinue(state -> {
            executed.set(Thread.currentThread());
            winnerExecuting.countDown();
            if (errors.checkSucceeds(asCallable(submissionsWithoutContinuation::await)) != TRUE) {
              return null;
            }
            errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(2));
            return null;
          });
          errors.checkThat("The current thread hasn't win the race to execute this action",
            executed.get(), is(Thread.currentThread()));
          errors.checkThat("Continuation isn't supposed to be null", continuation, notNullValue());
          continuationResult.complete(continuation);
        });
        executorService.submit(() -> {
          if (errors.checkSucceeds(asCallable(winnerExecuting::await)) != TRUE) {
            return;
          }
          AtomicReference<Thread> executed = new AtomicReference<>();
          for (int i = 0; i < 2; i++) {
            final Executor.Continuation continuation = sync.submitAndContinue(state -> {
              Thread existing = executed.getAndSet(Thread.currentThread());
              if (existing != null) {
                errors.checkThat("This action isn't supposed to be executed twice by different threads",
                  existing, is(Thread.currentThread()));
              }
              return null;
            });
            errors.checkThat("Continuation is supposed to be null", continuation, nullValue());
          }
          submissionsWithoutContinuation.countDown();
          errors.checkThat("Action isn't supposed to be executed", executed.get(), nullValue());
          errors.checkThat("expecting one action still left to happen", sync.actions(), is(2));
          final Executor.Continuation winnerContinuation = errors.checkSucceeds(continuationResult::get);
          if (winnerContinuation == null) {
            return;
          }
          final Executor.Continuation lastContinuation = winnerContinuation.resume();
          errors.checkThat("Expecting another continuation", lastContinuation, notNullValue());
          errors.checkThat("There should be just another action left", sync.actions(), is(1));
          errors.checkThat("The current thread hasn't win the race to resume its pending action", executed.get(), is(Thread.currentThread()));
          errors.checkThat("Further continuation isn't supposed to be needed", lastContinuation.resume(), nullValue());
          errors.checkThat("There shouldn't be any action left", sync.actions(), is(0));
          errors.checkThat("The current thread hasn't win the race to resume its pending action", executed.get(), is(Thread.currentThread()));
        }).get();
      }
    } finally {
      executorService.shutdown();
    }
  }

  private static class TimeMachine implements LongSupplier {

    private final AtomicLong currentTime = new AtomicLong(0);

    @Override
    public long getAsLong() {
      return currentTime.get();
    }

    public long moveForward(Duration time) {
      return currentTime.addAndGet(time.toNanos());
    }

    public long rewind(Duration time) {
      return currentTime.addAndGet(-time.toNanos());
    }

    public void rewind() {
      currentTime.set(0);
    }
  }

  @Test
  public void cannotResumeTwiceContinuationIfNotSuspended() throws InterruptedException {
    final CombinerExecutor<Object> sync = new CombinerExecutor<>(new Object(), actions -> true);
    final CountDownLatch executing = new CountDownLatch(1);
    final CountDownLatch accumulated = new CountDownLatch(1);
    Thread executor = new Thread(() -> {
      final Executor.Continuation continuation = sync.submitAndContinue(state -> {
        executing.countDown();
        if (errors.checkSucceeds(asCallable(accumulated::await)) != TRUE) {
          return null;
        }
        errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(1));
        return null;
      });
      errors.checkThat("continuation is supposed to not be null", continuation, notNullValue());
      errors.checkThat("continuation is supposed to be null: no actions left to execute", continuation.resume(), nullValue());
      // cannot resume it again!
      errors.checkThrows(IllegalStateException.class, continuation::resume);
    });
    executor.start();
    Thread accumulator = new Thread(() -> {
      if (errors.checkSucceeds(asCallable(executing::await)) != TRUE) {
        return;
      }
      sync.submitAndContinue(state -> null);
      accumulated.countDown();
    });
    accumulator.start();
    accumulator.join();
    executor.join();
  }

  @Test
  public void notEmitContinuationIfAlreadySuspended() throws InterruptedException, ExecutionException {
    final CombinerExecutor<Object> sync = new CombinerExecutor<>(new Object(), actions -> true);
    final CountDownLatch executing = new CountDownLatch(1);
    final CountDownLatch accumulated = new CountDownLatch(1);
    final CompletableFuture<Executor.Continuation> resume = new CompletableFuture<>();
    Thread executor = new Thread(() -> {
      final Executor.Continuation continuation = sync.submitAndContinue(state -> {
        executing.countDown();
        if (errors.checkSucceeds(asCallable(accumulated::await)) != TRUE) {
          return null;
        }
        errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(2));
        return null;
      });
      errors.checkThat("continuation is supposed to not be null", continuation, notNullValue());
      resume.complete(continuation);
    });
    executor.start();
    Thread accumulator = new Thread(() -> {
      if (errors.checkSucceeds(asCallable(executing::await)) != TRUE) {
        return;
      }
      class Executed {
        int value = 0;
      }
      Executed executed = new Executed();
      for (int i = 0; i < 2; i++) {
        sync.submitAndContinue(state -> {
          executed.value++;
          return null;
        });
      }
      accumulated.countDown();
      // this is going to await the concurrent submission to complete
      Executor.Continuation continuation = errors.checkSucceeds(resume::get);
      errors.checkThat("continuation is supposed to be null; no resume has happened yet", sync.submitAndContinue(state -> {
        executed.value++;
        return null;
      }), nullValue());
      errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(2));
      errors.checkThat(executed.value, is(1));
      continuation = continuation.resume();
      errors.checkThat(continuation, notNullValue());
      errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(1));
      errors.checkThat(executed.value, is(2));
      errors.checkThat(continuation.resume(), nullValue());
      errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(0));
      errors.checkThat(executed.value, is(3));
    });
    accumulator.start();
    accumulator.join();
    executor.join();
  }

  @Test
  public void yieldWithTimeDeadlineControlExecutionDuration() throws InterruptedException, ExecutionException {
    final TimeMachine timeMachine = new TimeMachine();
    final Duration TIMEOUT_EXE = Duration.ofMillis(100);
    final Duration MORE_THEN_TIMEOUT_EXE = TIMEOUT_EXE.plusNanos(1);
    final CombinerExecutor<Object> sync = new CombinerExecutor<>(new Object(), CombinerExecutor.YieldCondition.withMaxDuration(TIMEOUT_EXE, timeMachine));
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    final int TEST_ITERATION = 10000;
    try {
      for (int t = 0; t < TEST_ITERATION; t++) {
        timeMachine.rewind();
        final CountDownLatch submissionsWithoutContinuation = new CountDownLatch(1);
        final CountDownLatch winnerExecuting = new CountDownLatch(1);
        CompletableFuture<Executor.Continuation> continuationResult = new CompletableFuture<>();
        executorService.execute(() -> {
          AtomicReference<Thread> executed = new AtomicReference<>();
          Executor.Continuation continuation = sync.submitAndContinue(state -> {
            timeMachine.moveForward(MORE_THEN_TIMEOUT_EXE);
            executed.set(Thread.currentThread());
            winnerExecuting.countDown();
            if (errors.checkSucceeds(asCallable(submissionsWithoutContinuation::await)) != TRUE) {
              return null;
            }
            errors.checkThat("pending actions doesn't match expected ones", sync.actions(), is(2));
            return null;
          });
          errors.checkThat("The current thread hasn't win the race to execute this action",
            executed.get(), is(Thread.currentThread()));
          errors.checkThat("Continuation isn't supposed to be null", continuation, notNullValue());
          continuationResult.complete(continuation);
        });
        executorService.submit(() -> {
          if (errors.checkSucceeds(asCallable(winnerExecuting::await)) != TRUE) {
            return;
          }
          AtomicReference<Thread> executed = new AtomicReference<>();
          for (int i = 0; i < 2; i++) {
            final Executor.Continuation continuation = sync.submitAndContinue(state -> {
              timeMachine.moveForward(MORE_THEN_TIMEOUT_EXE);
              Thread existing = executed.getAndSet(Thread.currentThread());
              if (existing != null) {
                errors.checkThat("This action isn't supposed to be executed twice by different threads",
                  existing, is(Thread.currentThread()));
              }
              return null;
            });
            errors.checkThat("Continuation is supposed to be null", continuation, nullValue());
          }
          submissionsWithoutContinuation.countDown();
          errors.checkThat("Action isn't supposed to be executed", executed.get(), nullValue());
          errors.checkThat("expecting one action still left to happen", sync.actions(), is(2));
          final Executor.Continuation winnerContinuation = errors.checkSucceeds(continuationResult::get);
          if (winnerContinuation == null) {
            return;
          }
          final Executor.Continuation lastContinuation = winnerContinuation.resume();
          errors.checkThat("Expecting another continuation", lastContinuation, notNullValue());
          errors.checkThat("There should be just another action left", sync.actions(), is(1));
          errors.checkThat("The current thread hasn't win the race to resume its pending action", executed.get(), is(Thread.currentThread()));
          errors.checkThat("Further continuation isn't supposed to be needed", lastContinuation.resume(), nullValue());
          errors.checkThat("There shouldn't be any action left", sync.actions(), is(0));
          errors.checkThat("The current thread hasn't win the race to resume its pending action", executed.get(), is(Thread.currentThread()));
        }).get();
      }
    } finally {
      executorService.shutdown();
    }
  }
}
