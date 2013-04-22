package org.vertx.java.tests.core.context;

import org.junit.Test;
import org.vertx.java.core.Context;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.testframework.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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
