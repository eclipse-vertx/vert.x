package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError ;
import org.junit.runners.BlockJUnit4ClassRunner;

public class LinuxOrOsx extends BlockJUnit4ClassRunner {

  public LinuxOrOsx(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  public void run(RunNotifier notifier) {
    if (PlatformDependent.isWindows()) {
      // Skip
    } else {
      super.run(notifier);
    }
  }
}
