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

import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ContextTest extends InstrumentationTestBase {

  @Test
  public void testRunOnContext() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.runOnContext(v1 -> {
      assertSame(cont, instrumentation.current());
      vertx.runOnContext(v2 -> {
        assertSame(cont, instrumentation.current());
        testComplete();
      });
    });
    cont.suspend();
    await();
  }

  @Test
  public void testExecuteBlocking() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.executeBlocking(fut -> {
      assertSame(cont, instrumentation.current());
      new Thread(fut::complete).start();
    }, ar -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    });
    cont.suspend();
    await();
  }

  @Test
  public void testSetTimer() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.setTimer(10, id -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    });
    cont.suspend();
    await();
  }

  @Test
  public void testSetPeriodic() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    AtomicInteger count = new AtomicInteger();
    vertx.setPeriodic(10, id -> {
      assertSame(cont, instrumentation.current());
      if (count.incrementAndGet() == 10) {
        vertx.cancelTimer(id);
        testComplete();
      }
    });
    cont.suspend();
    await();
  }

  @Test
  public void testDoubleDipInSameContinuation() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    AtomicReference<Handler<Void>> expected = new AtomicReference<>();
    Handler<Void> handler = event -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    };
    handler = ((VertxInternal)vertx).captureContinuation(handler);
    expected.set(handler);
    Handler<Void> doubleDip = ((VertxInternal) vertx).captureContinuation(handler);
    cont.suspend();
    assertSame(handler, doubleDip);
    doubleDip.handle(null);
    await();
  }

  // Corner case to investigate
  @Test(expected = AssertionError.class)
  public void testDoubleDipInDifferentContinuation() throws Exception {
    TestContinuation cont1 = instrumentation.continuation();
    cont1.resume();
    AtomicReference<Handler<Void>> expected = new AtomicReference<>();
    Handler<Void> handler = event -> {
      assertSame(cont1, instrumentation.current());
      testComplete();
    };
    handler = ((VertxInternal)vertx).captureContinuation(handler);
    expected.set(handler);
    cont1.suspend();
    TestContinuation cont2 = instrumentation.continuation();
    cont2.resume();
    Handler<Void> doubleDip = ((VertxInternal) vertx).captureContinuation(handler);
    assertSame(handler, doubleDip);
    doubleDip.handle(null);
    await();
  }
}
