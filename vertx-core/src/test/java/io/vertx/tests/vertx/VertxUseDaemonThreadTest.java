/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.vertx;

import io.vertx.core.VertxOptions;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:palomino219@gmail.com">Duo Zhang</a>
 */
@RunWith(Parameterized.class)
public class VertxUseDaemonThreadTest extends VertxTestBase {

  @Parameter
  public Boolean useDaemonThread;

  @Parameters(name = "{index}: useDaemonThread={0}")
  public static List<Object[]> params() {
    return Arrays.asList(new Object[] {true}, new Object[] {false}, new Object[] {null});
  }

  @Override
  protected VertxOptions getOptions() {
    return super.getOptions().setUseDaemonThread(useDaemonThread);
  }

  @Test
  public void testUseDaemonThread() {
    vertx.runOnContext(v -> {
      Thread current = Thread.currentThread();
      if (useDaemonThread != null) {
        assertEquals(useDaemonThread.booleanValue(), current.isDaemon());
      } else {
        // null means do not change the daemon flag, so it should be non daemon
        assertFalse(current.isDaemon());
      }
      testComplete();
    });
    await();
  }
}
