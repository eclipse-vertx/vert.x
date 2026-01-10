package io.vertx.core.spi;

import java.time.Duration;
import java.util.concurrent.ThreadFactory;

public interface ThreadFactoryProvider {

  ThreadFactory threadFactory(String prefix, boolean worker, Duration maxExecuteTime);

}
