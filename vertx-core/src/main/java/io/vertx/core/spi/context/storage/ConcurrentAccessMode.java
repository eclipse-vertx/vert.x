package io.vertx.core.spi.context.storage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

final class ConcurrentAccessMode implements AccessMode {

  public static final ConcurrentAccessMode INSTANCE = new ConcurrentAccessMode();

  private static final VarHandle LOCALS_UPDATER = MethodHandles.arrayElementVarHandle(Object[].class);

  private ConcurrentAccessMode() {
  }

  @Override
  public Object get(Object[] locals, int idx) {
    return LOCALS_UPDATER.getVolatile(locals, idx);
  }

  @Override
  public void put(Object[] locals, int idx, Object value) {
    LOCALS_UPDATER.setRelease(locals, idx, value);
  }

  @Override
  public Object getOrCreate(Object[] locals, int idx, Supplier<Object> initialValueSupplier) {
    Object res;
    while (true) {
      res = LOCALS_UPDATER.getVolatile(locals, idx);
      if (res != null) {
        break;
      }
      Object initial = initialValueSupplier.get();
      if (initial == null) {
        throw new IllegalStateException();
      }
      if (LOCALS_UPDATER.compareAndSet(locals, idx, null, initial)) {
        res = initial;
        break;
      }
    }
    return res;
  }
}


