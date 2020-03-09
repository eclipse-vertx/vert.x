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
package io.vertx.core;

import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class FutureOperatorsTest extends VertxTestBase {

  @Test
  public void testIssue_1() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = ctx.promise();
    Future<String> fut1 = promise.future();
    fut1.onComplete(ar -> {
      System.out.println(ar.succeeded());
      System.out.println(ar.failed());
      AsyncResult<String> mapped = ar.map("Mapped");
      System.out.println(mapped.succeeded());
      System.out.println(mapped.failed());
    });
    promise.complete("Value");
//    await();
  }

  @Test
  public void testIssue_2() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = ctx.promise();
    Future<String> fut1 = promise.future();
    Future<String> fut2 = fut1.map("Mapped");
    fut2.onComplete(ar -> {
      System.out.println("result");
    });
    promise.complete("Value");
//    await();
  }
}
