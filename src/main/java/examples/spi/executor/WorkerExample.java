/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package examples.spi.executor;

import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

/**
 * This code could be any valid Vert.x worker API code as there are no API
 * changes or restrictions introduced by using an SPI provided executor.
 */
public class WorkerExample {

  public void workerExecutor3(Vertx vertx) {

    int poolSize = 5;
    long maxExecuteTime = 1;

    // The Vert.x worker executor API is the same regardless of SPI use, e.g.:
    WorkerExecutor executor = vertx.createSharedWorkerExecutor("my-worker-pool", poolSize, maxExecuteTime, TimeUnit.MINUTES);

    // The executor delegates to one created by the SPI provided
    // ExecutorServiceFactory
    executor.executeBlocking(promise -> {
      String result = blockingService("tired");
      promise.complete(result);
    }, res -> {
      System.out.println("The result is: " + res.result());
    });
  }

  // Imagine this is some longer running action
  String blockingService(String str) {
    System.out.println("Zzzz");
    return "that's better!";
  }

}
