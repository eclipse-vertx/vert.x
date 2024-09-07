/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.it.vertx;

import io.vertx.core.Vertx;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ExecutorServiceFactoryTest extends VertxTestBase {

  @Test
  public void testExecuteBlocking() throws Exception {
    int initialValue = CustomExecutorServiceFactory.NUM.get();
    Vertx vertx = Vertx.vertx();
    try {
      assertEquals(initialValue + 2, CustomExecutorServiceFactory.NUM.get());
      int num = 10;
      CountDownLatch latch = new CountDownLatch(num);
      for (int i = 0;i < num;i++) {
        vertx.executeBlocking(() -> {
          assertTrue(CustomExecutorService.executing.get());
          return null;
        }).onComplete(onSuccess(v -> {
          latch.countDown();
        }));
      }
      awaitLatch(latch);
    } finally {
      vertx.close().await(30, TimeUnit.SECONDS);
    }
    assertEquals(initialValue, CustomExecutorServiceFactory.NUM.get());
  }
}
