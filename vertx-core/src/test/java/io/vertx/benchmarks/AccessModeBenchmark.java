package io.vertx.benchmarks;

import io.vertx.core.spi.context.storage.AccessMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
public class AccessModeBenchmark {

  /**
   * This is to mimic https://shipilev.net/jvm/anatomy-quarks/17-trust-nonstatic-final-fields/#_practice
   * the static_final case, despite anonymous classes "seems" to not accept static final fields while declared
   * on interfaces, they won't provide the same semantics for final fields, which instead are "just" final fields (!!)
   */
  private interface OldAccessMode {

    AccessMode CONCURRENT = new AccessMode() {

      final VarHandle LOCALS_UPDATER = MethodHandles.arrayElementVarHandle(Object[].class);

      @Override
      public Object get(Object[] locals, int idx) {
        return LOCALS_UPDATER.getVolatile(locals, idx);
      }

      @Override
      public void put(Object[] locals, int idx, Object value) {
        LOCALS_UPDATER.setRelease(locals, idx, value);
      }

      @Override
      public Object getOrCreate(Object[] locals, int index, Supplier<Object> initialValueSupplier) {
        Object res;
        while (true) {
          res = LOCALS_UPDATER.getVolatile(locals, index);
          if (res != null) {
            break;
          }
          Object initial = initialValueSupplier.get();
          if (initial == null) {
            throw new IllegalStateException();
          }
          if (LOCALS_UPDATER.compareAndSet(locals, index, null, initial)) {
            res = initial;
            break;
          }
        }
        return res;
      }
    };
  }

  @State(Scope.Thread)
  public static class FakeLocalStorage {

    final Object[] locals;

    public FakeLocalStorage() {
      locals = new Object[1];
    }

    public Object get(int index, AccessMode accessMode) {
      return accessMode.get(locals, index);
    }

    public void put(int index, Object value, AccessMode accessMode) {
      accessMode.put(locals, index, value);
    }
  }

  public enum AccessModeType {
    OLD, NEW
  }

  private int index;

  @Setup
  public void setup() {
    index = 0;
  }

  @Benchmark
  public Object getOld(FakeLocalStorage fakeLocalStorage) {
    return fakeLocalStorage.get(index, OldAccessMode.CONCURRENT);
  }

  @Benchmark
  public void putOld(FakeLocalStorage fakeLocalStorage) {
    fakeLocalStorage.put(index, Boolean.TRUE, OldAccessMode.CONCURRENT);
  }

  @Benchmark
  public Object getNew(FakeLocalStorage fakeLocalStorage) {
    return fakeLocalStorage.get(index, AccessMode.CONCURRENT);
  }

  @Benchmark
  public void putNew(FakeLocalStorage fakeLocalStorage) {
    fakeLocalStorage.put(index, Boolean.TRUE, AccessMode.CONCURRENT);
  }

}
