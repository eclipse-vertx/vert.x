/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.tests.platform;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.testframework.TestBase;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PullInDepsTest extends TestBase {

  public static final String MOD_TEST_BASE = System.getProperty("vertx.mods", "src/test/mod-test");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    vertx.fileSystem().deleteSync(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods", true);
  }

  @Test
  public void testPullInDeps() throws Exception {
    final String deployID = startMod("io.vertx~mod-maven-server~1.0", null, 1, false);
    final CountDownLatch latch = new CountDownLatch(1);
    platformManager.pullInDependencies("io.vertx~mod-pullin~1.0", new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        assertTrue(res.succeeded());
        try {
          stopApp(deployID, false);
        } catch (Exception e) {
          fail("caught exception");
        }
        assertFileExists(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods");
        assertFileExists(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods/io.vertx~mod-pullin-a~2.0.0");
        assertFileExists(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods/io.vertx~mod-pullin-b~1.0.1");
        assertFileExists(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods/io.vertx~mod-pullin-c~0.1");
        assertFileExists(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods/io.vertx~mod-pullin-d~1.2-beta");
        // Nested
        assertFileExists(MOD_TEST_BASE + "/io.vertx~mod-pullin~1.0/mods/io.vertx~mod-pullin-d~1.2-beta/mods/io.vertx~mod-pullin-e~2.2");
        latch.countDown();
      }
    });
    try {
      if (!latch.await(20000, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out");
      }
    } catch (InterruptedException e) {
    }
  }

  private void assertFileExists(String fileName) {
    assertTrue(new File(fileName).exists());
  }


}

