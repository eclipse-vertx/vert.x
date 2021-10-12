package io.vertx.core.spi.classloading;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ClassLoaderSupplier implements Supplier<ClassLoader> {

  private volatile ClassLoader classLoader;
  private final List<Consumer<ClassLoader>> listeners = new CopyOnWriteArrayList<>();

  public ClassLoaderSupplier(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public ClassLoader get() {
    return classLoader;
  }

  public void addListener(Consumer<ClassLoader> changeListener) {
    Objects.requireNonNull(changeListener);
    listeners.add(changeListener);
  }

  public void removeListener(Consumer<ClassLoader> changeListener) {
    Objects.requireNonNull(changeListener);
    listeners.remove(changeListener);
  }

  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
    for (Consumer<ClassLoader> i : listeners) {
      i.accept(classLoader);
    }
  }
}
