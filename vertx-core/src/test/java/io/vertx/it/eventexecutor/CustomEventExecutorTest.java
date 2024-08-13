/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.eventexecutor;

import io.vertx.core.Context;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CustomEventExecutorTest {

  private Vertx vertx;
  private Context context;

  @Before
  public void before() {
    vertx = Vertx.vertx();
  }

  @After
  public void after() {
    vertx.close();
  }

  @Test
  public void testCustomEventExecutor() throws Exception {
    CustomThread thread = new CustomThread(() -> {
      context = vertx.getOrCreateContext();
    });
    thread.start();
    thread.join();
    assertEquals(ThreadingModel.OTHER, context.threadingModel());
    int[] executions = new int[1];
    context.runOnContext(v -> {
      executions[0]++;
    });
    assertTrue(CustomEventExecutorProvider.hasNext());
    Runnable runnable = CustomEventExecutorProvider.next();
    runnable.run();
    assertFalse(CustomEventExecutorProvider.hasNext());
    assertEquals(1, executions[0]);
  }
}
