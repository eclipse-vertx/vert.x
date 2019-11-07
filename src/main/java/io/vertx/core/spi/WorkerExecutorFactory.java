package io.vertx.core.spi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import io.vertx.core.VertxOptions;

/**
 * Factory for the {@link java.util.concurrent.ExecutorService} invoking blocking operations.
 */
public interface WorkerExecutorFactory {
  ExecutorService createExecutorService(VertxOptions options, ThreadFactory threadFactory);
}
