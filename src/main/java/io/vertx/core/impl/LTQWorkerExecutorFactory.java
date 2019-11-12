package io.vertx.core.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.vertx.core.VertxOptions;
import io.vertx.core.spi.WorkerExecutorFactory;

public class LTQWorkerExecutorFactory implements WorkerExecutorFactory {
  public static final LTQWorkerExecutorFactory INSTANCE = new LTQWorkerExecutorFactory();

  @Override
  public ExecutorService createExecutorService(VertxOptions options, ThreadFactory threadFactory) {
    int workerPoolSize = options.getWorkerPoolSize();
    return new ThreadPoolExecutor(workerPoolSize, workerPoolSize, 0L, TimeUnit.MILLISECONDS,
      new LinkedTransferQueue<>(), threadFactory);
  }
}
