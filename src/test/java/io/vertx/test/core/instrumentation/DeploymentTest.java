/*
 * Copyright 2015 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DeploymentTest extends InstrumentationTestBase {

  @Test
  public void testDeployVerticle() {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        assertNull(instrumentation.current());
      }
      @Override
      public void stop() {
        assertNull(instrumentation.current());
      }
    }, onSuccess(id -> {
      assertSame(cont, instrumentation.current());
      vertx.undeploy(id, onSuccess(v -> {
        assertSame(cont, instrumentation.current());
        testComplete();
      }));
    }));
    cont.suspend();
    await();
  }
}
