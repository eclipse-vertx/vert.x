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

package org.vertx.java.tests.core.context;

import org.junit.Test;
import org.vertx.java.core.Context;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.testframework.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JavaContextTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testRunOnContext() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final Vertx vertx = VertxFactory.newVertx();
    vertx.runOnContext(new VoidHandler() {
      @Override
      protected void handle() {
        final Thread th = Thread.currentThread();
        final Context ctx = vertx.currentContext();
        ctx.runOnContext(new VoidHandler() {
          @Override
          protected void handle() {
            assertTrue(Thread.currentThread() == th);
            assertTrue(vertx.currentContext() == ctx);
            // And simulate a third party thread - e.g. a 3rd party async library wishing to return a result on the
            // correct context
            new Thread() {
              public void run() {
                ctx.runOnContext(new VoidHandler() {
                  @Override
                  protected void handle() {
                    assertTrue(Thread.currentThread() == th);
                    assertTrue(vertx.currentContext() == ctx);
                    latch.countDown();
                  }
                });
              }
            }.start();
          }
        });
      }
    });
    while (true) {
      try {
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        break;
      } catch (InterruptedException ignore) {
      }
    }
  }

  @Test
  public void testNoContext() throws Exception {
    Vertx vertx = VertxFactory.newVertx();
    assertNull(vertx.currentContext());
  }
}
